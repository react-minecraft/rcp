package com.g4vrk.react.inference.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import org.jetbrains.annotations.NotNull;

/**
 * Reads a response body into memory, failing with {@link TooLargeException} past a byte limit.
 */
public final class LimitedBodyHandler implements HttpResponse.BodyHandler<byte[]> {

    private final int maximumBytes;

    public LimitedBodyHandler(final int maximumBytes) {
        this.maximumBytes = maximumBytes;
    }

    @Override
    public HttpResponse.BodySubscriber<byte[]> apply(final HttpResponse.ResponseInfo responseInfo) {
        return new LimitedBodySubscriber(maximumBytes);
    }

    public static boolean isTooLarge(final @NotNull Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof TooLargeException) {
                return true;
            }
        }
        return false;
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int maximumBytes;
        private final ByteArrayOutputStream output;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();

        private Flow.Subscription subscription;

        private LimitedBodySubscriber(final int maximumBytes) {
            this.maximumBytes = maximumBytes;
            this.output = new ByteArrayOutputStream(Math.min(maximumBytes, 256));
        }

        @Override
        public @NotNull CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(final @NotNull Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(final @NotNull List<ByteBuffer> buffers) {
            for (final ByteBuffer buffer : buffers) {
                final int remaining = buffer.remaining();

                if (remaining > maximumBytes - output.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new TooLargeException(maximumBytes));
                    return;
                }

                final byte[] bytes = new byte[remaining];
                buffer.get(bytes);
                output.write(bytes, 0, bytes.length);
            }

            subscription.request(1);
        }

        @Override
        public void onError(final @NotNull Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }
    }

    public static final class TooLargeException extends IOException {

        private static final long serialVersionUID = 1L;

        private TooLargeException(final int maximumBytes) {
            super("HTTP response exceeds " + maximumBytes + " bytes");
        }
    }
}
