package su.plo.voice.plugin.template;

public final class PlasmoVoiceVersionCheck {
    private PlasmoVoiceVersionCheck() {
    }

    public static String unsupportedMessage(String version, String minVersion) {
        String[] versionParts = version.split("\\D+");
        String[] minVersionParts = minVersion.split("\\.");

        for (int i = 0; i < minVersionParts.length; i++) {
            int versionPart = i < versionParts.length ? Integer.parseInt(versionParts[i]) : 0;
            int minVersionPart = Integer.parseInt(minVersionParts[i]);

            if (versionPart > minVersionPart) return null;
            if (versionPart < minVersionPart) {
                return "PlasmoVoice " + minVersion + " or newer is required, but found " + version;
            }
        }

        return null;
    }
}
