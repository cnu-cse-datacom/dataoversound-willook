package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import android.widget.Toast;
public class Listentone {

    int HANDSHAKE_START_HZ = 4096;
    int HANDSHAKE_END_HZ = 5120 + 1024;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    int FEC_BYTES = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    public AudioRecord mAudioRecord = null;
    int audioEncodig;
    boolean startFlag;
    FastFourierTransformer transform;
    public void PreRequest(){
        Queue<Double> packet = new LinkedList<Double>();
        short[] buffer;
        while(true){

            //int blocksize = findPowerSize((int)(long)Math.round(interval/2*mSampleRate));
            int blocksize = (int)(interval*mSampleRate/2);
            //pprint("blocksize:",blocksize);
            buffer = new short[blocksize];
            int bufferedReadResult = mAudioRecord.read(buffer,0,blocksize);
            if(bufferedReadResult != blocksize) continue;
            //pprint("result: ", buffer[0]);
            double dom = findFrequency(buffer);
            if (startFlag)
                pprint("freq:",dom);

            if (startFlag && match(dom, HANDSHAKE_END_HZ)){
                // 8bits byte -128~127
                pprint("packet len: ",packet.size());
                byte[] byteStream = extractPacket(packet);
                pprint("bytes len: ",byteStream.length);
                String s = byteArrayToString(byteStream);
                pprint(s);
                startFlag = false;
            }
            else if (startFlag){
                packet.add(dom);
            }
            else if (match(dom, HANDSHAKE_START_HZ)){
                startFlag = true;
            }
        }

    }
    private void pprint(String s){
        Log.d("ListentoneCHUNK", s);
    }
    private void pprint(int i){
        Log.d("ListentoneCHUNK", Integer.toString(i));
    }
    private void pprint(String s, int i){
        Log.d("ListentoneCHUNK", (s+" "+Integer.toString(i)));
    }
    private void pprint(String s, short i){
        Log.d("ListentoneCHUNK", (s+" "+Integer.toString((int)i)));
    }
    private void pprint(String s, double d){
        Log.d("ListentoneCHUNK", (s+" "+Double.toString(d)));
    }
    private String byteArrayToString(byte[] byteSteam){
        StringBuffer sb = new StringBuffer();
        int curBit = 8-BITS;
        char ch = 0;
        for(int i=0;i<byteSteam.length;i++){
            //pprint("bytes: ",byteSteam[i]);
            ch += byteSteam[i] << curBit;
            curBit = curBit - BITS;
            if(curBit == -1*BITS){
                //pprint("ch: ",(int)ch);
                curBit = 8-BITS;
                sb.append(ch);
                ch = 0;
            }
        }
        return sb.toString();
    }

    public Listentone(){
        //Log.d("Listentone", "hello world!");
        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
        mAudioRecord.startRecording();
    }
    private byte[] extractPacket(Queue<Double> packet){
        int len = packet.size()/2+packet.size()%2;
        byte[] bytes = new byte[len];
        //Discard start hz
        if(!packet.isEmpty()) packet.remove();
        if(!packet.isEmpty()) packet.remove();
        int top = 0;
        while(!packet.isEmpty()){
            double cur = Math.round((packet.remove()-START_HZ)/STEP_HZ);
            if(cur >= 0 && cur < (1<<BITS)){
                bytes[top] = (byte)(cur);
                top += 1;
            }
            if(!packet.isEmpty()) packet.remove();
        }
        byte[] ret = new byte[top];
        for(int i=0;i<top;i++)
            ret[i] = bytes[i];

        return ret;
    }
    private boolean match(double freq1, double freq2){
        return Math.abs(freq1-freq2) < 20;
    }
    private int getBuffer(AudioRecord mAudioRecord, short[] buffer){
        int blocksize = findPowerSize((int)(long)Math.round(interval/2*mSampleRate));
        buffer = new short[blocksize];
        int bufferedReadResult = mAudioRecord.read(buffer,0,blocksize);

        return bufferedReadResult;
    }
    private int findPowerSize(int size){
        int ret = 1;
        while(ret < size)
            ret *= 2;
        return ret;
    }
    private double findFrequency(short[] buffer){
        int valuelen = buffer.length;
        int len = findPowerSize(valuelen);
        double[] toTransform = new double[len];
        // Zero padding with right side of array because we don't use hanning window
        for(int i=0;i<toTransform.length;i++) {
            if(i<valuelen) toTransform[i] = (double) buffer[i];
            else toTransform[i] = 0;
        }
        double[] real = new double[len];
        double[] img = new double[len];
        double realNum, imgNum;
        double[] mag = new double[len];

        Complex[] complx = transform.transform(toTransform,TransformType.FORWARD);
        Double[] freqs = this.fftfreq(complx.length,1);
        int idx = 0;
        for(int i=0;i<complx.length;i++){
            realNum = complx[i].getReal();
            imgNum = complx[i].getImaginary();
            mag[i] = Math.sqrt((realNum*realNum)+(imgNum*imgNum));
            idx = mag[idx] > mag[i] ? idx : i;
        }

        return Math.abs(freqs[idx]);
    }
    private Double[] fftfreq(int size, double duration){
        Double[] freqList = new Double[size];
        for(int i=0;i<size;i++) {
            freqList[i] = ((double) i) * mSampleRate / size;
            if (freqList[i] >= mSampleRate/2)
                freqList[i] -= mSampleRate;
        }
        return freqList;
    }
}
