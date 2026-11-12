#include "whisper.h"
#include <algorithm>
#include <chrono>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>
static std::string quoted(const std::string &s) {
    std::string out="\"";
    for (unsigned char c:s) {
        if(c=='"'||c=='\\') {out+='\\';out+=c;}
        else if(c=='\n') out+="\\n";
        else if(c=='\r') out+="\\r";
        else if(c=='\t') out+="\\t";
        else if(c>=32) out+=c;
    }
    return out+'"';
}
int main(int argc,char**argv) {
    if(argc<2 || argc>3)return 1;
    const int max_tokens = argc == 3 ? std::stoi(argv[2]) : 128;
    auto cp=whisper_context_default_params();cp.use_gpu=false;
    auto *ctx=whisper_init_from_file_with_params(argv[1],cp);if(!ctx)return 2;
    std::string path; int start,count,translate;
    while(std::cin>>path>>start>>count>>translate) {
        std::ifstream in(path,std::ios::binary);in.seekg(start*sizeof(float));
        std::vector<float> pcm(count);in.read(reinterpret_cast<char*>(pcm.data()),count*sizeof(float));
        count=in.gcount()/sizeof(float);if(count<=0)return 3;
        auto p=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        p.print_realtime=false;p.print_progress=false;p.print_timestamps=false;p.print_special=false;
        p.translate=translate;p.language="pt";p.n_threads=2;p.offset_ms=0;
        p.no_context=true;p.single_segment=false;p.no_timestamps=true;p.suppress_blank=true;
        p.temperature=0.0f;p.temperature_inc=0.0f;p.max_tokens=max_tokens;
        p.no_speech_thold=.55f;p.entropy_thold=2.4f;p.logprob_thold=-.8f;
        auto t=std::chrono::steady_clock::now();int rc=whisper_full(ctx,p,pcm.data(),count);
        auto ms=std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-t).count();
        std::string text;float prob=0;
        if(rc==0)for(int i=0;i<whisper_full_n_segments(ctx);i++) {
            text+=whisper_full_get_segment_text(ctx,i);
            prob=std::max(prob,whisper_full_get_segment_no_speech_prob(ctx,i));
        }
        std::cout<<"{\"rc\":"<<rc<<",\"ms\":"<<ms<<",\"p\":"<<prob<<",\"text\":"<<quoted(text)<<"}"<<std::endl;
    }
    whisper_free(ctx);
}
