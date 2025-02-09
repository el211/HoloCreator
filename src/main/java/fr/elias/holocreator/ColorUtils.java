package fr.elias.holocreator;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing and applying colors, gradients, and formatting in Minecraft messages.
 */
public class ColorUtils {

    private static boolean chatDebug = false;

    /**
     * Initializes the chat debug option from the config.
     *
     * @param config The plugin's configuration file.
     */
    public static void initialize(FileConfiguration config) {
        chatDebug = config.getBoolean("chatdebug", false);
    }

    /**
     * Parses a message to apply gradients, hex colors, <bold> tags, and legacy (&) color codes.
     *
     * @param message The message to process.
     * @return The fully formatted message with all colors and formatting applied.
     */
    public static String parseAllColors(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        debugLog("[DEBUG] Original Message: " + message);

        // Step 1: Parse standalone hex colors (e.g., &#RRGGBB or <#RRGGBB>)
        message = applyHexColors(message);
        debugLog("[DEBUG] After Hex Color Parsing: " + message);

        // Step 2: Parse gradients (and handle nested <bold> tags within gradients)
        message = parseGradients(message);
        debugLog("[DEBUG] After Gradient Parsing: " + message);

        // Step 3: Replace <bold> tags with Minecraft's §l bold formatting
        message = message.replace("<bold>", ChatColor.BOLD.toString()).replace("</bold>", "");
        debugLog("[DEBUG] After Bold Replacement: " + message);

        // Step 4: Translate legacy color codes (& to §)
        message = ChatColor.translateAlternateColorCodes('&', message);
        debugLog("[DEBUG] After Legacy Code Parsing: " + message);

        // Step 5: Clean up any leftover invalid tags
        message = message.replaceAll("<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>", ""); // Remove unparsed gradient tags
        message = message.replaceAll("</gradient>", ""); // Remove closing gradient tags
        debugLog("[DEBUG] After Cleaning Up Leftover Tags: " + message);

        return message;
    }
    /**
     * Checks if chat debugging is enabled.
     *
     * @return true if chatdebug is enabled in the config, false otherwise.
     */
    public static boolean isChatDebugEnabled() {
        return chatDebug;
    }

    /**
     * Parses gradient tags in a string and replaces them with gradient-colored text.
     * Supports <gradient:#startHex:#endHex>text</gradient> syntax, including optional <bold>.
     *
     * @param message The message to process.
     * @return The message with gradients and formatting applied.
     */
    public static String parseGradients(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        debugLog("[DEBUG] Parsing Gradients: " + message);

        // Regex pattern to match gradient tags in the format <gradient:#startHex:#endHex>text</gradient>
        Pattern gradientPattern = Pattern.compile("<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)</gradient>");
        Matcher matcher = gradientPattern.matcher(message);

        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String startHex = ensureHashPrefix(matcher.group(1)); // Start hex color
            String endHex = ensureHashPrefix(matcher.group(2));   // End hex color
            String text = matcher.group(3);                      // Text to apply gradient

            // Check if the text contains <bold> tags
            boolean isBold = text.contains("<bold>");
            text = text.replace("<bold>", "").replace("</bold>", ""); // Remove <bold> tags before processing

            // Apply gradient to the text
            String gradientText = applyGradient(text, startHex, endHex, isBold);

            matcher.appendReplacement(buffer, gradientText);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * Applies a gradient effect to a string of text.
     *
     * @param message  The message to apply the gradient to.
     * @param startHex The starting hex color of the gradient.
     * @param endHex   The ending hex color of the gradient.
     * @param isBold   Whether to apply bold formatting to the gradient text.
     * @return The text with a gradient effect applied.
     */
    public static String applyGradient(String message, String startHex, String endHex, boolean isBold) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        StringBuilder gradientMessage = new StringBuilder();
        int length = message.length();
        int[] startRgb = hexToRgb(startHex);
        int[] endRgb = hexToRgb(endHex);

        for (int i = 0; i < length; i++) {
            float ratio = (float) i / (float) (length - 1); // Ratio for blending colors
            int r = (int) (startRgb[0] + ratio * (endRgb[0] - startRgb[0]));
            int g = (int) (startRgb[1] + ratio * (endRgb[1] - startRgb[1]));
            int b = (int) (startRgb[2] + ratio * (endRgb[2] - startRgb[2]));

            ChatColor color = ChatColor.of(new java.awt.Color(r, g, b));
            gradientMessage.append(color);
            if (isBold) {
                gradientMessage.append(ChatColor.BOLD); // Append bold formatting if applicable
            }
            gradientMessage.append(message.charAt(i));
        }
        return gradientMessage.toString();
    }

    /**
     * Applies hex colors (e.g., &#FF5733 or <#FF5733>) to a string by converting them into Minecraft-compatible color codes.
     *
     * @param message The message to process.
     * @return The formatted message with hex colors applied.
     */
    public static String applyHexColors(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // Regex pattern to match hex color codes in the format &#RRGGBB or <#RRGGBB>
        Pattern hexPattern = Pattern.compile("(&#([A-Fa-f0-9]{6}))|(<#([A-Fa-f0-9]{6})>)");
        Matcher matcher = hexPattern.matcher(message);

        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hexCode = ensureHashPrefix(matcher.group(2) != null ? matcher.group(2) : matcher.group(4)); // Get hex code
            matcher.appendReplacement(buffer, ChatColor.of(hexCode).toString());
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * Logs debug messages if chatdebug is enabled in the config.
     *
     * @param message The debug message to log.
     */
    private static void debugLog(String message) {
        if (chatDebug) {
            Bukkit.getLogger().info(message);
        }
    }

    /**
     * Converts a hex color code (e.g., #FF5733) to an RGB array.
     *
     * @param hex The hex color code.
     * @return An array containing the RGB values.
     */
    private static int[] hexToRgb(String hex) {
        if (hex == null || hex.isEmpty() || hex.length() != 7 || !hex.startsWith("#")) {
            throw new IllegalArgumentException("Invalid hex color code: " + hex);
        }
        return new int[]{
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)
        };
    }

    /**
     * Ensures a hex string starts with '#' if it does not already.
     *
     * @param hex The hex string.
     * @return The hex string prefixed with '#'.
     */
    private static String ensureHashPrefix(String hex) {
        if (hex != null && !hex.startsWith("#")) {
            return "#" + hex;
        }
        return hex;
    }
}
