package example.avatar;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InfraiClient {
    private static final Pattern IMAGE = Pattern.compile("\\\"image\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;

    public InfraiClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String upload(byte[] file, String filename) throws IOException, InterruptedException {
        String body = "{\"file\":\"" + Base64.getEncoder().encodeToString(file) + "\",\"filename\":\"" + json(filename) + "\"}";
        return returnedImage(post("/v1/image/upload", body));
    }

    public String crop(String image, int size) throws IOException, InterruptedException {
        // Infrai capability: image.crop
        return returnedImage(post("/v1/image/crop", "{\"image\":\"" + json(image) + "\",\"x\":0,\"y\":0,\"width\":" + size + ",\"height\":" + size + "}"));
    }

    public String resize(String image) throws IOException, InterruptedException {
        return returnedImage(post("/v1/image/resize", "{\"image\":\"" + json(image) + "\",\"width\":256,\"height\":256,\"fit\":\"cover\",\"enlarge\":true,\"format\":\"jpeg\",\"store\":true}"));
    }

    public String updateUser(String userId, String image) throws IOException, InterruptedException {
        String body = "{\"user_id\":\"" + json(userId) + "\",\"metadata\":{\"avatar\":\"" + json(image) + "\"}}";
        return request("PATCH", "/v1/auth/user/update/" + jsonPath(userId), body);
    }

    private String post(String path, String body) throws IOException, InterruptedException {
        return request("POST", path, body);
    }

    static String returnedImage(String envelope) throws IOException {
        Matcher match = IMAGE.matcher(envelope);
        if (!match.find()) throw new IOException("Infrai image response did not contain an image");
        return unescapeJson(match.group(1));
    }

    private static String unescapeJson(String value) throws IOException {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\') {
                result.append(current);
                continue;
            }
            if (++i == value.length()) throw new IOException("Invalid JSON escape in Infrai image response");
            char escaped = value.charAt(i);
            switch (escaped) {
                case '\"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (i + 4 >= value.length()) throw new IOException("Invalid Unicode escape in Infrai image response");
                    try {
                        result.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                    } catch (NumberFormatException error) {
                        throw new IOException("Invalid Unicode escape in Infrai image response", error);
                    }
                    i += 4;
                }
                default -> throw new IOException("Invalid JSON escape in Infrai image response");
            }
        }
        return result.toString();
    }

    private String request(String method, String path, String body) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (envelope.contains("\"ok\":false")) throw new IOException("Infrai request rejected: " + envelope);
            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep((long) Math.pow(2, attempt) * 250L);
                continue;
            }
            if (response.statusCode() >= 500) throw new IOException("Infrai service response: " + response.statusCode());
            return envelope;
        }
        throw new IOException("Infrai request could not be completed");
    }

    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String jsonPath(String value) { return value.replace("/", "%2F"); }
}
