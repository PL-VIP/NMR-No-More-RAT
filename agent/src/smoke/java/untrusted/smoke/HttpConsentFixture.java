package untrusted.smoke;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Benign traffic only, called by the loopback test harness. */
public final class HttpConsentFixture {
    public static int send(HttpClient client, HttpRequest request, int mode) throws Exception {
        if (mode == 1) return client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).get().statusCode();
        if (mode == 2) return client.sendAsync(request, HttpResponse.BodyHandlers.discarding(), null).get().statusCode();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
