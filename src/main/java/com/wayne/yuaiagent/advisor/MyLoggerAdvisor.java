package com.wayne.yuaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 自定义日志 Advisor（拦截器）
 * 作用：在 AI 对话前后打印日志，方便调试和观察
 *
 * 实现两个接口：
 * - CallAdvisor: 拦截普通对话（一次性返回完整结果）
 * - StreamAdvisor: 拦截流式对话（逐字返回结果）
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

	/**
	 * 返回这个 Advisor 的名称
	 * 用于日志和调试时标识是哪个 Advisor
	 *
	 * @return Advisor 的类名，例如 "MyLoggerAdvisor"
	 */
	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	/**
	 * 返回执行顺序优先级
	 * 数字越小，越先执行
	 *
	 * 场景：如果有多个 Advisor，需要决定谁先拦截
	 * - 0 = 最高优先级（最先执行）
	 * - 100 = 较低优先级（较后执行）
	 *
	 * @return 优先级数字
	 */
	@Override
	public int getOrder() {
		return 0;
	}

	/**
	 * 在发送请求给 AI 之前执行的预处理方法
	 * 作用：记录用户的问题（Prompt）
	 *
	 * @param request 包含用户问题的请求对象
	 * @return 原样返回请求（不做修改）
	 */
	private ChatClientRequest before(ChatClientRequest request) {
		// 打印日志：用户问了什么
		log.info("AI Request: {}", request.prompt());
		return request;
	}

	/**
	 * 在收到 AI 回答后执行的后处理方法
	 * 作用：记录 AI 的回答内容
	 *
	 * @param chatClientResponse 包含 AI 回答的响应对象
	 */
	private void observeAfter(ChatClientResponse chatClientResponse) {
		// 打印日志：AI 回答了什麼
		// 解析路径：response → chatResponse → result → output → text
		log.info("AI Response: {}", chatClientResponse.chatResponse().getResult().getOutput().getText());
	}

	/**
	 * 拦截普通对话（非流式）
	 * 这是 CallAdvisor 接口的核心方法
	 *
	 * 执行流程：
	 * 1. 调用 before() 记录用户问题
	 * 2. 调用 chain.nextCall() 继续执行下一个 Advisor 或发送给 AI
	 * 3. 调用 observeAfter() 记录 AI 回答
	 * 4. 返回 AI 的回答
	 *
	 * @param chatClientRequest 用户请求
	 * @param chain 责任链，用于传递给下一个 Advisor
	 * @return AI 的完整回答
	 */
	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
		// 第1步：发送前，记录用户问题
		chatClientRequest = before(chatClientRequest);

		// 第2步：继续执行链条（传给下一个 Advisor 或 AI）
		// 这一步会真正调用 AI API，等待完整回答
		ChatClientResponse chatClientResponse = chain.nextCall(chatClientRequest);

		// 第3步：收到回答后，记录 AI 的回答
		observeAfter(chatClientResponse);

		// 第4步：返回回答
		return chatClientResponse;
	}

	/**
	 * 拦截流式对话（逐字返回）
	 * 这是 StreamAdvisor 接口的核心方法
	 *
	 * 流式对话特点：
	 * - 不是一次性返回完整答案
	 * - 而是像打字机一样，一个字一个字地返回
	 * - 适合需要实时显示的场景（如聊天界面）
	 *
	 * 执行流程：
	 * 1. 调用 before() 记录用户问题
	 * 2. 调用 chain.nextStream() 获取流式响应（Flux）
	 * 3. 使用 MessageAggregator 聚合流式数据
	 * 4. 当所有数据接收完毕后，调用 observeAfter() 记录完整回答
	 *
	 * @param chatClientRequest 用户请求
	 * @param chain 责任链，用于传递给下一个 Advisor
	 * @return 流式响应（Flux）
	 */
	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
		// 第1步：发送前，记录用户问题
		chatClientRequest = before(chatClientRequest);

		// 第2步：继续执行链条，获取流式响应
		// Flux 是响应式编程的数据流，会陆续发出多个响应片段
		Flux<ChatClientResponse> chatClientResponseFlux = chain.nextStream(chatClientRequest);

		// 第3步：聚合流式数据，并在完成后记录日志
		// ChatClientMessageAggregator 会把多个小片段拼成完整回答
		// 当所有片段接收完毕后，自动调用 observeAfter() 记录完整回答
		return (new ChatClientMessageAggregator()).aggregateChatClientResponse(chatClientResponseFlux, this::observeAfter);
	}
}
