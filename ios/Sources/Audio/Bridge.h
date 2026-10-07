#pragma once
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
void *atm_create(int channels);
void atm_destroy(void *engine);
int atm_add(void *engine, const char *path, int64_t frames);
void atm_mix(void *engine, int index, float gain, float pan, int route);
void atm_click(void *engine, double bpm, int beats, float gain, int accent, int sound, int route, int64_t offset);
void atm_loop(void *engine, int64_t start, int64_t end, int enabled);
void atm_start(void *engine, int64_t frame, int64_t total);
void atm_stop(void *engine);
void atm_read(void *engine, float *output, int frames);
int64_t atm_position(void *engine);
int64_t atm_underruns(void *engine);
float atm_peak(void *engine);
float atm_track_peak(void *engine, int index);
int atm_stretch(const char *source, const char *destination, int64_t frames, double speed, int semitones);
double atm_yin(const float *samples, int count, double rate, double *rms, double *confidence);
#ifdef __cplusplus
}
#endif
