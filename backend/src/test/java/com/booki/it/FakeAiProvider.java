package com.booki.it;

import com.booki.ai.AiProvider;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hand-written test double for {@link AiProvider} — replaces every AI call in
 * integration tests. Deterministic, no network, no Mockito.
 *
 * <p>Two ways to control replies:
 * <ul>
 *   <li>{@link #enqueue(String)} — scripted FIFO replies for a specific call
 *       (for exercising error/degradation paths).</li>
 *   <li>Prompt-based defaults — the reply is chosen by what the prompt asks
 *       for (grading frame, quiz question, summary, ...), so happy-path flows
 *       work without any scripting.</li>
 * </ul>
 * Every call is recorded ({@link #calls()}) so tests can assert on the exact
 * system prompt the backend assembled.
 */
public class FakeAiProvider implements AiProvider {

    /** One recorded model call. */
    public record Call(String systemPrompt, List<Message> context, String userMessage) {
    }

    /** Matches QuizServiceImpl#generateQuiz's multi-question instruction (see its {@code instruction} string). */
    private static final Pattern QUIZ_INSTRUCTION = Pattern.compile(
            "Write exactly (\\d+) quiz questions covering pages (\\d+) to (\\d+)");

    private final Queue<String> scripted = new ConcurrentLinkedQueue<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    /** Reply script taking precedence over the prompt-based defaults. */
    public void enqueue(String reply) {
        scripted.add(reply);
    }

    public List<Call> calls() {
        return calls;
    }

    public void clear() {
        calls.clear();
        scripted.clear();
    }

    @Override
    public String model() {
        return "fake-model";
    }

    @Override
    public String key() {
        return "fake";
    }

    @Override
    public String converse(String systemPrompt, List<Message> context, String userMessage) {
        calls.add(new Call(systemPrompt, List.copyOf(context), userMessage));
        String reply = scripted.poll();
        if (reply != null) {
            return reply;
        }
        Matcher quiz = QUIZ_INSTRUCTION.matcher(userMessage);
        if (quiz.find()) {
            return multiQuestionReply(Integer.parseInt(quiz.group(1)),
                    Integer.parseInt(quiz.group(2)), Integer.parseInt(quiz.group(3)));
        }
        // Match phrases that appear ONLY in a forFunction() system prompt (locked
        // frames / fn_* bodies), never in a plain chat turn — the capability
        // router lists every capability's description, so chat prompts now
        // mention "memory aid", "question", etc.
        if (systemPrompt.contains("Reply in exactly this format and nothing else:")) {
            return "SCORE: 0.85\nFEEDBACK: You've got the main idea; the page also notes the second reason.";
        }
        if (systemPrompt.contains("Output only the question")) {
            return "What is the main idea of this page?";
        }
        if (systemPrompt.contains("Write prose only. No headings")) {
            return "Summary of the pages.";
        }
        if (systemPrompt.contains("Build one memory aid whose form fits")) {
            return "BEE: Bold ideas become memorable.";
        }
        if (systemPrompt.contains("explain that point from the ground up")) {
            return "In plain words, it works like a library companion.";
        }
        return "Fake AI reply";
    }

    /** N "PAGE: x\nQUESTION: ..." blocks spread evenly across [startPage, endPage], matching what generateQuiz asked for. */
    private static String multiQuestionReply(int questionCount, int startPage, int endPage) {
        int span = Math.max(1, endPage - startPage);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < questionCount; i++) {
            int page = questionCount == 1 ? startPage
                    : startPage + (int) Math.round((double) i * span / (questionCount - 1));
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("PAGE: ").append(page).append("\nQUESTION: Fake question about page ").append(page).append('?');
        }
        return sb.toString();
    }
}
