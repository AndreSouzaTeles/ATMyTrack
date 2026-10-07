#include "Bridge.h"
#include "signalsmith-stretch.h"
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

namespace {
constexpr int block = 512, slots = 16, rate = 48000;
struct Track {
    FILE *file = nullptr;
    int64_t frames = 0, cursor = -1;
    float gain = 1, pan = 0, previous = 0;
    std::atomic<float> peak{0};
    int route = -1;
    ~Track() { if(file) fclose(file); }
};
struct Core {
    int channels;
    std::vector<std::unique_ptr<Track>> tracks;
    std::array<std::vector<float>, slots> ring;
    std::array<int64_t, slots> positions{};
    std::atomic<uint64_t> read{0}, write{0};
    std::atomic<int64_t> position{0}, underruns{0};
    std::atomic<float> peak{0};
    std::atomic<bool> running{false};
    std::thread worker;
    std::mutex controls;
    int offset = 0;
    int64_t cursor = 0, total = 0, loopStart = 0, loopEnd = 0, clickOffset = 0;
    bool looping = false, accent = true;
    double bpm = 70;
    int beats = 4, sound = 0, clickRoute = -1;
    float clickGain = 0;
    explicit Core(int n): channels(std::clamp(n,2,16)) {
        for(auto &b:ring) b.resize(block*channels);
    }
    ~Core() { stop(); }
    void stop() { running=false; if(worker.joinable())worker.join(); }
    void route(float *dst, float left, float right, int route) {
        if(route==-2)dst[0]+=(left+right)*.5f;
        else if(route==-3)dst[1]+=(left+right)*.5f;
        else if(route>=100 && route-100<channels)dst[route-100]+=(left+right)*.5f;
        else { int c=route>=0?route:0; if(c+1<channels){dst[c]+=left;dst[c+1]+=right;} }
    }
    void produce() {
        std::array<float,block*2> input{};
        while(running) {
            if(write.load()-read.load()>=slots) { std::this_thread::sleep_for(std::chrono::milliseconds(1));continue; }
            auto w=write.load(); auto &dst=ring[w%slots]; std::fill(dst.begin(),dst.end(),0);
            std::lock_guard<std::mutex> lock(controls); // producer only; never in audio callback
            for(int base=0;base<block;) {
                if(looping && loopEnd>loopStart && cursor>=loopEnd)cursor=loopStart;
                int64_t end=looping&&loopEnd>loopStart?std::min(total,loopEnd):total;
                int n=static_cast<int>(std::min<int64_t>(block-base,std::max<int64_t>(0,end-cursor)));
                if(n==0)break;
                for(auto &p:tracks) {
                    auto &t=*p; std::fill(input.begin(),input.end(),0);
                    float trackPeak=0;
                    int count=static_cast<int>(std::min<int64_t>(n,std::max<int64_t>(0,t.frames-cursor)));
                    if(count>0) {
                        if(t.cursor!=cursor)fseeko(t.file,cursor*2*sizeof(float),SEEK_SET);
                        auto got=fread(input.data(),sizeof(float)*2,count,t.file); t.cursor=cursor+got;
                    }
                    for(int i=0;i<n;++i) {
                        float g=t.previous+(t.gain-t.previous)*(i+1)/n;
                        trackPeak=std::max(trackPeak,std::max(std::abs(input[i*2]*g),std::abs(input[i*2+1]*g)));
                        route(dst.data()+(base+i)*channels,input[i*2]*g*(1-std::max(0.f,t.pan)),input[i*2+1]*g*(1+std::min(0.f,t.pan)),t.route);
                    }
                    t.previous=t.gain;
                    t.peak.store(trackPeak);
                }
                if(clickGain>0 && bpm>0) {
                    double period=rate*60/bpm;
                    for(int i=0;i<n;++i) {
                        double sample=cursor+i-clickOffset;
                        if(sample<0)continue;
                        auto beat=static_cast<int64_t>(sample/period);
                        double phase=sample-beat*period;
                        if(phase<rate*.035) {
                            double hz=(accent&&beat%std::max(1,beats)==0?1800:1100)*(1+.12*sound);
                            float value=clickGain*std::sin(6.283185307*hz*phase/rate)*std::exp(-phase/(rate*.007));
                            route(dst.data()+(base+i)*channels,value,value,clickRoute);
                        }
                    }
                }
                cursor+=n;base+=n;
            }
            float maximum=0; for(float v:dst)maximum=std::max(maximum,std::abs(v));
            // Linked safety limiter, same scalar on all physical outputs.
            if(maximum>.98f)for(float &v:dst)v*=.98f/maximum;
            peak.store(std::min(maximum,1.f)); positions[w%slots]=cursor;
            write.store(w+1,std::memory_order_release);
        }
    }
};
}
extern "C" {
void *atm_create(int n) { try{return new Core(n);}catch(...){return nullptr;} }
void atm_destroy(void *p) {delete static_cast<Core*>(p);}
int atm_add(void *p,const char *path,int64_t frames) {
    auto c=static_cast<Core*>(p); if(c->running)return -1;
    auto t=std::make_unique<Track>();t->file=fopen(path,"rb");t->frames=frames;
    if(!t->file)return -1;c->tracks.push_back(std::move(t));return static_cast<int>(c->tracks.size()-1);
}
void atm_mix(void *p,int i,float gain,float pan,int route) {
    auto c=static_cast<Core*>(p);std::lock_guard<std::mutex> l(c->controls);
    if(i<0||i>=static_cast<int>(c->tracks.size()))return;
    c->tracks[i]->gain=gain;c->tracks[i]->pan=pan;c->tracks[i]->route=route;
}
void atm_click(void *p,double bpm,int beats,float gain,int accent,int sound,int route,int64_t offset) {
    auto c=static_cast<Core*>(p);std::lock_guard<std::mutex> l(c->controls);
    c->bpm=bpm;c->beats=beats;c->clickGain=gain;c->accent=accent;c->sound=sound;c->clickRoute=route;c->clickOffset=offset;
}
void atm_loop(void *p,int64_t start,int64_t end,int enabled) {
    auto c=static_cast<Core*>(p);std::lock_guard<std::mutex> l(c->controls);c->loopStart=start;c->loopEnd=end;c->looping=enabled;
}
void atm_start(void *p,int64_t frame,int64_t total) {
    auto c=static_cast<Core*>(p);c->stop();c->cursor=frame;c->position=frame;c->total=total;
    c->read=0;c->write=0;c->offset=0;c->running=true;
    c->worker=std::thread([c]{c->produce();});
    for(int i=0;i<200&&c->write<4;++i)std::this_thread::sleep_for(std::chrono::milliseconds(1));
}
void atm_stop(void *p) {static_cast<Core*>(p)->stop();}
void atm_read(void *p,float *out,int frames) {
    auto c=static_cast<Core*>(p);int filled=0;
    while(filled<frames) {
        auto r=c->read.load();
        if(r==c->write.load(std::memory_order_acquire)) {
            std::fill(out+filled*c->channels,out+frames*c->channels,0);
            if(c->running)c->underruns++;return;
        }
        int n=std::min(frames-filled,block-c->offset);
        std::copy_n(c->ring[r%slots].data()+c->offset*c->channels,n*c->channels,out+filled*c->channels);
        c->offset+=n;filled+=n;
        if(c->offset==block) {c->position=c->positions[r%slots];c->offset=0;c->read.store(r+1,std::memory_order_release);}
    }
}
int64_t atm_position(void *p) {return static_cast<Core*>(p)->position;}
int64_t atm_underruns(void *p) {return static_cast<Core*>(p)->underruns;}
float atm_peak(void *p) {return static_cast<Core*>(p)->peak;}
float atm_track_peak(void *p,int index) {
    auto c=static_cast<Core*>(p);
    return index>=0&&index<static_cast<int>(c->tracks.size())?c->tracks[index]->peak.load():0;
}
int atm_stretch(const char *source,const char *destination,int64_t frames,double speed,int semitones) {
    if(speed<.5||speed>2||frames<0)return -1;
    FILE *in=fopen(source,"rb"),*out=fopen(destination,"wb");
    if(!in||!out){if(in)fclose(in);if(out)fclose(out);return -1;}
    int result=0;
    try {
        signalsmith::stretch::SignalsmithStretch<float> dsp{0};dsp.configure(2,5760,480);dsp.setTransposeSemitones(semitones);
        std::array<float,4096> l{},r{},ol{},orr{};std::array<float,8192> data{};
        float *inputs[]={l.data(),r.data()},*outputs[]={ol.data(),orr.data()};
        int64_t consumed=0,generated=0,written=0;
        int64_t target=llround(frames/speed),skip=llround(dsp.inputLatency()/speed+dsp.outputLatency());
        while(written<target) {
            int n=1024;std::fill(data.begin(),data.end(),0);fread(data.data(),sizeof(float)*2,n,in);
            for(int i=0;i<n;++i){l[i]=data[i*2];r[i]=data[i*2+1];}
            consumed+=n;int m=static_cast<int>(llround(consumed/speed)-generated);dsp.process(inputs,n,outputs,m);generated+=m;
            int start=static_cast<int>(std::min<int64_t>(skip,m));skip-=start;
            int count=static_cast<int>(std::min<int64_t>(m-start,target-written));
            for(int i=0;i<count;++i){data[i*2]=ol[start+i];data[i*2+1]=orr[start+i];}
            if(fwrite(data.data(),sizeof(float)*2,count,out)!=static_cast<size_t>(count)) {result=-2;break;}
            written+=count;
        }
        if(ferror(in))result=-3;
    }catch(...){result=-4;}
    fclose(in);if(fclose(out)!=0)result=-5;if(result)remove(destination);return result;
}
double atm_yin(const float *s,int count,double rate,double *rms,double *confidence) {
    *rms=0;*confidence=0;if(count<1024)return 0;
    double mean=0;for(int i=0;i<count;++i)mean+=s[i];mean/=count;
    for(int i=0;i<count;++i)*rms+=(s[i]-mean)*(s[i]-mean);*rms=std::sqrt(*rms/count);
    if(*rms<.002)return 0;
    int maxLag=std::min(count/2-2,static_cast<int>(rate/25)),minLag=std::max(2,static_cast<int>(rate/1600));
    std::vector<double>d(maxLag+1,1);double sum=0;
    for(int lag=1;lag<=maxLag;++lag) {double v=0;for(int i=0;i<count/2;++i){double delta=s[i]-s[i+lag];v+=delta*delta;}sum+=v;d[lag]=sum>0?v*lag/sum:1;}
    for(int lag=minLag;lag<maxLag;++lag)if(d[lag]<.12) {
        while(lag+1<maxLag&&d[lag+1]<d[lag])lag++;
        *confidence=1-d[lag];if(*confidence<.9)return 0;
        double denominator=d[lag-1]-2*d[lag]+d[lag+1];
        double delta=std::abs(denominator)>1e-12?.5*(d[lag-1]-d[lag+1])/denominator:0;
        return rate/(lag+std::clamp(delta,-1.,1.));
    }return 0;
}
}
