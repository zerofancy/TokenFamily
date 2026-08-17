package top.ntutn.tokenfamily.aidl;

import top.ntutn.tokenfamily.aidl.ChatCompletionRequest;
import top.ntutn.tokenfamily.aidl.ChatCompletionResponse;
import top.ntutn.tokenfamily.aidl.IChatStreamCallback;

interface IChatCompletionService {
    ChatCompletionResponse chat(in ChatCompletionRequest request);

    ChatCompletionResponse streamChat(in ChatCompletionRequest request, IChatStreamCallback callback);

    oneway void cancelStream(String requestId);

    ChatCompletionResponse listModels();
}
