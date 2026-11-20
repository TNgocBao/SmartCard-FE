package vn.kma.desktop.api;

import java.io.IOException;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Enforces a byte limit while the response is received, rather than after allocating an unbounded string. */
public final class BoundedJsonBodyHandler implements HttpResponse.BodyHandler<String> {
    private final long limit;
    public BoundedJsonBodyHandler(long limit){if(limit<1)throw new IllegalArgumentException();this.limit=limit;}
    public HttpResponse.BodySubscriber<String> apply(HttpResponse.ResponseInfo info){return subscriber();}
    public HttpResponse.BodySubscriber<String> subscriber(){
        return new HttpResponse.BodySubscriber<>() {
            private final HttpResponse.BodySubscriber<String> delegate=HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
            private Flow.Subscription subscription;private long count;private boolean failed;
            public CompletionStage<String> getBody(){return delegate.getBody();}
            public void onSubscribe(Flow.Subscription value){subscription=value;delegate.onSubscribe(value);}
            public void onNext(List<ByteBuffer> buffers){
                if(failed)return;
                for(var buffer:buffers)count+=buffer.remaining();
                if(count>limit){failed=true;subscription.cancel();delegate.onError(new ClientStateException("RESPONSE_TOO_LARGE"));return;}
                delegate.onNext(buffers);
            }
            public void onError(Throwable error){if(!failed){failed=true;delegate.onError(error);}}
            public void onComplete(){if(!failed)delegate.onComplete();}
        };
    }
}
