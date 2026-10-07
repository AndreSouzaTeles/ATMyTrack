#include <jni.h>
#include <vector>
#include "signalsmith-stretch.h"

struct Pitch {
    signalsmith::stretch::SignalsmithStretch<float> stretch{0};
    std::vector<float> in[2], out[2], interleaved;
    Pitch(int rate,int semitones) {
        stretch.configure(2,static_cast<int>(rate*.12),static_cast<int>(rate*.01)); stretch.setTransposeSemitones(semitones);
        for(int i=0;i<2;++i) { in[i].resize(4096); out[i].resize(4096); }
        interleaved.resize(8192);
    }
};
extern "C" JNIEXPORT jlong JNICALL Java_com_atmytrack_app_audio_NativePitch_create(JNIEnv* env,jobject,jint rate,jint semitones) {
    try { return reinterpret_cast<jlong>(new Pitch(rate,semitones)); }
    catch(...) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"Falha ao iniciar DSP"); return 0; }
}
extern "C" JNIEXPORT jint JNICALL Java_com_atmytrack_app_audio_NativePitch_latency(JNIEnv*,jobject,jlong h) {
    auto p=reinterpret_cast<Pitch*>(h); return p->stretch.inputLatency()+p->stretch.outputLatency();
}
extern "C" JNIEXPORT void JNICALL Java_com_atmytrack_app_audio_NativePitch_process(JNIEnv* env,jobject,jlong h,jfloatArray source,jfloatArray target,jint count) {
    auto p=reinterpret_cast<Pitch*>(h);
    if(count<0 || count>4096 || env->GetArrayLength(source)<count*2 || env->GetArrayLength(target)<count*2) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),"Bloco DSP inválido"); return;
    }
    env->GetFloatArrayRegion(source,0,count*2,p->interleaved.data());
    for(int i=0;i<count;++i) { p->in[0][i]=p->interleaved[i*2]; p->in[1][i]=p->interleaved[i*2+1]; }
    float* inputs[]={p->in[0].data(),p->in[1].data()}; float* outputs[]={p->out[0].data(),p->out[1].data()};
    p->stretch.process(inputs,count,outputs,count);
    for(int i=0;i<count;++i) { p->interleaved[i*2]=p->out[0][i]; p->interleaved[i*2+1]=p->out[1][i]; }
    env->SetFloatArrayRegion(target,0,count*2,p->interleaved.data());
}
extern "C" JNIEXPORT void JNICALL Java_com_atmytrack_app_audio_NativePitch_destroy(JNIEnv*,jobject,jlong h) { delete reinterpret_cast<Pitch*>(h); }
extern "C" JNIEXPORT jint JNICALL Java_com_atmytrack_app_audio_NativePitch_stretchLatency(JNIEnv*,jobject,jlong h,jdouble speed) {
    auto p=reinterpret_cast<Pitch*>(h); return (int)std::round(p->stretch.inputLatency()/speed+p->stretch.outputLatency());
}
extern "C" JNIEXPORT void JNICALL Java_com_atmytrack_app_audio_NativePitch_stretch(JNIEnv* env,jobject,jlong h,jfloatArray source,jfloatArray target,jint n,jint m) {
    auto p=reinterpret_cast<Pitch*>(h);
    if(n<1 || n>4096 || m<1 || m>4096 || env->GetArrayLength(source)<n*2 || env->GetArrayLength(target)<m*2) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),"Bloco DSP inválido");return;
    }
    env->GetFloatArrayRegion(source,0,n*2,p->interleaved.data());
    for(int i=0;i<n;++i) { p->in[0][i]=p->interleaved[i*2];p->in[1][i]=p->interleaved[i*2+1]; }
    float* inputs[]={p->in[0].data(),p->in[1].data()};float* outputs[]={p->out[0].data(),p->out[1].data()};
    p->stretch.process(inputs,n,outputs,m);
    for(int i=0;i<m;++i) { p->interleaved[i*2]=p->out[0][i];p->interleaved[i*2+1]=p->out[1][i]; }
    env->SetFloatArrayRegion(target,0,m*2,p->interleaved.data());
}
