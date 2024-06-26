package seedu.duke.parser;

import seedu.duke.command.Command;
import seedu.duke.exceptions.ParserException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class CommandMetadata {

    private static Logger logger = Logger.getLogger("CommandMetadata");
    private static Map<String, String> argRegexMap = new HashMap<>();

    static {
        logger.setLevel(Level.OFF);

        argRegexMap.put("name", "n/(?<name>[A-Za-z]+(?: [A-Za-z]+)*)");
        argRegexMap.put("currentSem", "curr/(?<currentSem>[1-8])");
        argRegexMap.put("graduationSem", "grad/(?<graduationSem>[1-8])");
        argRegexMap.put("courseCode", "c/(?<courseCode>[a-zA-Z0-9]+)");
        argRegexMap.put("semester", "w/(?<semester>[1-8])");
        argRegexMap.put("mc", "m/(?<mc>[1-9]|1[0-2])");
        argRegexMap.put("grade", "g/(?<grade>[ab][+-]?|[cd][+]?|f|cs|s)");
        argRegexMap.put("dgpa", "(?<dgpa>[0-9]+([.][0-9]*)?|[.][0-9]+)");
    }

    private String keyword;
    private String[] groupArguments;
    private String[] groupArgumentFlags;
    private String regex;
    private int regexLength;
    private Pattern pattern;

    protected CommandMetadata(String keyword, String[] groupArguments) throws IllegalArgumentException {
        this(keyword, groupArguments, null);
    }

    protected CommandMetadata(String keyword, String[] groupArguments, String[] groupArgumentFlags)
            throws IllegalArgumentException {
        if (keyword == null || groupArguments == null) {
            throw new IllegalArgumentException("Keyword, regex, and group arguments cannot be null");
        }
        this.keyword = keyword;
        this.groupArguments = groupArguments;

        if (groupArgumentFlags == null) {
            groupArgumentFlags = new String[groupArguments.length];
            Arrays.fill(groupArgumentFlags, "mandatory"); // default flag
        }
        if (groupArgumentFlags.length != groupArguments.length) {
            throw new IllegalArgumentException("Group Argument and Group Argument Flags does not have the same size!");
        }

        this.groupArgumentFlags = groupArgumentFlags;

        this.regex = generateRegex(keyword, groupArguments, groupArgumentFlags);
        this.regexLength = groupArguments.length + 1; // Keyword + number of Arguments
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    protected String getKeyword() {
        return keyword;
    }

    protected String getRegex() {
        return regex;
    }

    protected Pattern getPattern() {
        return pattern;
    }

    protected String[] getGroupArguments() {
        return groupArguments;
    }

    protected String getArgRegex(String groupName) {
        return argRegexMap.get(groupName);
    }

    protected Map<String, String> getArgRegexMap() {
        return argRegexMap;
    }

    private static String generateRegex(String keyword, String[] groupArguments, String[] groupArgumentFlags) {
        if (keyword == null || groupArguments == null || groupArgumentFlags == null) {
            throw new IllegalArgumentException("Keyword, groupArguments and flags must not be null");
        }
        assert groupArgumentFlags.length == groupArguments.length : "Arguments and Flags must be of the same size!";

        StringBuilder regexPattern = new StringBuilder(keyword);
        for (int i = 0; i < groupArguments.length; i++) {
            if (groupArgumentFlags[i].equals("optional")) {
                appendOptionalRegex(regexPattern, groupArguments[i]);
            } else {
                appendRegex(regexPattern, groupArguments[i]);
            }
        }
        return regexPattern.toString();
    }

    // Helper function for generateRegex
    private static void appendRegex(StringBuilder regexPattern, String groupArgName) {
        String argRegex = argRegexMap.get(groupArgName);
        if (argRegex == null) {
            throw new IllegalArgumentException("No regex pattern found for argument: " + groupArgName);
        }
        regexPattern.append("\\s+").append(argRegex);
    }

    private static void appendOptionalRegex(StringBuilder regexPattern, String groupArgName) {
        String argRegex = argRegexMap.get(groupArgName);
        if (argRegex == null) {
            throw new IllegalArgumentException("No regex pattern found for argument: " + groupArgName);
        }
        String optionalGroup = "(\\s+" + argRegex + ")?";
        regexPattern.append(optionalGroup);
    }

    protected boolean matchesKeyword(String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return false;
        }

        userInput = userInput.trim();
        String[] userInputParts = userInput.split("\\s+");
        if (userInputParts[0].equalsIgnoreCase(keyword)) {
            return true;
        }
        return false;
    }

    protected Map<String, String> getCommandArguments(Matcher matcher) {
        Map<String, String> arguments = new HashMap<>();

        assert groupArguments != null : "groupArgument should be initialised at this point";
        for (String groupArgument : groupArguments) {
            String argument = matcher.group(groupArgument);
            if (argument == null) {
                continue;
            }

            if (groupArgument.equals("courseCode") || groupArgument.equals("grade")) {
                argument = argument.toUpperCase();
            }
            arguments.put(groupArgument, argument);
        }

        return arguments;
    }

    private void validateUserArguments(String argument, String argumentName) throws ParserException {
        String argRegex = argRegexMap.get(argumentName);
        assert argRegex != null : "Regex pattern for " + argumentName + " should already be placed in argRegexMap";

        Pattern pattern = Pattern.compile(argRegex);
        Matcher matcher = pattern.matcher(argument);

        if (!matcher.matches()) {
            logger.log(Level.INFO, "Regex pattern: " + argRegex);
            logger.log(Level.INFO, "UserInput Argument: " + argument);
            throw new ParserException("Invalid " + keyword + " command: Invalid " + argumentName + " format/order");
        }
    }

    /**
     * Splits the user input string into parts based on the regular expressions defined for each argument.
     * Eg. Given String = "init n/John Doe curr/4 grad/6" : Return String[] ["init", "n/John Doe", "curr/4", "grad/6"]
     * Requires use of groupArguments and argRegexMap
     *
     * @param userInput The user input string to be split.
     * @return An array of strings containing the parts of the user input.
     */
    private String[] splitUserInput(String userInput) {
        // Build the regex pattern to split the userInput
        StringBuilder regexPatternBuilder = new StringBuilder("\\s+(?=");
        for (String str : groupArguments) {
            // Get the delimiter by regex replacement and append to the regex pattern
            String delimiter = getArgRegex(str).replaceFirst("\\(\\?<[^>]+>.*\\)", "");
            regexPatternBuilder.append(delimiter).append("|");
        }
        // Remove the trailing "|" character and close the lookahead assertion
        regexPatternBuilder.deleteCharAt(regexPatternBuilder.length() - 1);
        regexPatternBuilder.append(")");

        // Convert regex pattern to string
        String regexSplitPattern = regexPatternBuilder.toString();

        // Split userInput based on the constructed regex pattern
        String[] userInputParts = userInput.split(regexSplitPattern);
        return userInputParts;
    }

    protected void validateUserInput(String userInput) throws IllegalArgumentException, ParserException {
        assert userInput != null : "userInput should not be null at this point";

        String[] userInputParts = splitUserInput(userInput);
        logger.log(Level.INFO, "userInputParts: " + Arrays.toString(userInputParts));
        assert userInputParts[0].equalsIgnoreCase(keyword) : "userInput should match keyword at this point";

        if (userInputParts.length != regexLength) {
            throw new ParserException("Invalid " + keyword + " command: Invalid argument format/delimiters used");
        }

        // Check user arguments
        if (groupArguments.length != userInputParts.length - 1) {
            throw new IllegalArgumentException("Regex length should be keyword + number of arguments");
        }
        for (int i = 0; i < groupArguments.length; i++) {
            validateUserArguments(userInputParts[i + 1], groupArguments[i]);
        }
    }

    protected abstract Command createCommandInstance(Map<String, String> args);
}
