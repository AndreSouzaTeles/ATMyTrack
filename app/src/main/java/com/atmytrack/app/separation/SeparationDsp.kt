package com.atmytrack.app.separation

import kotlin.math.*

/** Radix-2 FFT, periodic Hann and weighted overlap-add. All positions use the source clock. */
object SeparationDsp {
    const val FFT=4096
    const val HOP=1024
    const val BINS=1024
    const val STEPS=512
    const val CONTEXT=64
    const val CORE=STEPS-2*CONTEXT
    val window=FloatArray(FFT) { (.5-.5*cos(2*PI*it/FFT)).toFloat() }
    fun fft(re:FloatArray,im:FloatArray,inverse:Boolean=false) {
        val n=re.size;require(n==im.size && n>0 && n and (n-1)==0)
        var j=0
        for(i in 1 until n) {
            var bit=n shr 1
            while(j and bit!=0) { j=j xor bit;bit=bit shr 1 }
            j=j xor bit
            if(i<j) { val r=re[i];re[i]=re[j];re[j]=r;val v=im[i];im[i]=im[j];im[j]=v }
        }
        var length=2
        while(length<=n) {
            val a=(if(inverse)2 else -2)*PI/length;val wr=cos(a).toFloat();val wi=sin(a).toFloat()
            for(start in 0 until n step length) {
                var ur=1f;var ui=0f
                for(k in 0 until length/2) {
                    val x=start+k;val y=x+length/2
                    val vr=re[y]*ur-im[y]*ui;val vi=re[y]*ui+im[y]*ur
                    re[y]=re[x]-vr;im[y]=im[x]-vi;re[x]+=vr;im[x]+=vi
                    val next=ur*wr-ui*wi;ui=ur*wi+ui*wr;ur=next
                }
            }
            length*=2
        }
        if(inverse)for(i in 0 until n) { re[i]/=n;im[i]/=n }
    }
    fun vocalMask(v:Float,a:Float):Float {
        require(v.isFinite() && a.isFinite()) { "O modelo produziu áudio inválido." }
        val vv=v.toDouble()*v;val aa=a.toDouble()*a
        return ((vv+5e-11)/(vv+aa+1e-10)).toFloat().coerceIn(0f,1f)
    }
}
