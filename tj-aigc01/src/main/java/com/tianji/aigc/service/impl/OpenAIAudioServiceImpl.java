package com.tianji.aigc.service.impl;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.tianji.aigc.service.AudioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.audio.speech.SpeechModel;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.SpeechResponse;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;


@Slf4j
@RequiredArgsConstructor
@Service
public class OpenAIAudioServiceImpl implements AudioService {
    private final OpenAiAudioSpeechModel openAiAudioSpeechModel;
    private final OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel;
    @Override
    public ResponseBodyEmitter ttsStream(String text) {
        // 定义响应对象
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();
        // 调用openai进行文字转语音
        SpeechPrompt speechPrompt = new SpeechPrompt(text);
        Flux<SpeechResponse> streamResponse = openAiAudioSpeechModel.stream(speechPrompt);
        streamResponse.subscribe(speechResponse -> {
                    try {
                        // 响应语音的内容
                        byte[] output = speechResponse.getResult().getOutput();
                        // 发送给前端
                        emitter.send(output);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                emitter::completeWithError,
                emitter::complete
                );
        return emitter;
    }
    /*
    语音转文字
     */
    @Override
    public String stt(MultipartFile multipartFile) {
        // 获取音频文件
        Resource resource = multipartFile.getResource();
        //  基于文件资源，创建一个音频转文字的提示
        AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource);
        // 调用openai进行音频转文字
        AudioTranscriptionResponse response = openAiAudioTranscriptionModel.call(prompt);
        // 获取文本内容
        String text = response.getResult().getOutput();
        log.info("音频转文字结果: {}", text);
        return ZhConverterUtil.toSimple(text);
    }
}
