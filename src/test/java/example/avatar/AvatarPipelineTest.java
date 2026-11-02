package example.avatar;

public final class AvatarPipelineTest {
    public static void main(String[] args) throws Exception {
        String image = InfraiClient.returnedImage("{\"ok\":true,\"data\":{\"image\":\"https:\\/\\/cdn.example\\/avatar\\u002Ejpg\"}}");
        if (!image.equals("https://cdn.example/avatar.jpg")) throw new AssertionError(image);

        RecordingClient client = new RecordingClient();
        AvatarPipelineService service = new AvatarPipelineService(client);
        service.process("patient-7", new byte[] {1, 2}, 640, 480);
        if (!client.calls.toString().equals("[upload, crop:uploaded, resize:cropped, update:resized]")) throw new AssertionError(client.calls);
        client.calls.clear();
        service.process("patient-7", new byte[] {1}, 256, 256);
        if (!client.calls.toString().equals("[upload, resize:uploaded, update:resized]")) throw new AssertionError(client.calls);
        System.out.println("AvatarPipelineTest: PASS");
    }

    private static final class RecordingClient extends InfraiClient {
        private final java.util.List<String> calls = new java.util.ArrayList<>();
        RecordingClient() { super("test", "http://localhost"); }
        public String upload(byte[] file, String filename) { calls.add("upload"); return "uploaded"; }
        public String crop(String image, int size) { calls.add("crop:" + image); return "cropped"; }
        public String resize(String image) { calls.add("resize:" + image); return "resized"; }
        public String updateUser(String userId, String image) { calls.add("update:" + image); return "updated"; }
    }
}
