package net.jsa2025.calcmod.commands.subcommands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.jsa2025.calcmod.CalcMod;
import net.jsa2025.calcmod.commands.CalcCommand;
import net.jsa2025.calcmod.commands.arguments.CCustomFunctionProvider;
import net.jsa2025.calcmod.commands.arguments.CustomFunctionProvider;
import net.jsa2025.calcmod.utils.CalcMessageBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.checkerframework.checker.units.qual.C;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Custom {

    public static final File commandFile = new File(".", "config/calcmod.json");
    public static LiteralArgumentBuilder<FabricClientCommandSource> register(LiteralArgumentBuilder<FabricClientCommandSource> command) {
        command = command.then(ClientCommandManager.literal("custom")
                .then(ClientCommandManager.literal("add")
                .then(ClientCommandManager.argument("name", StringArgumentType.string())
                        .then(ClientCommandManager.argument("function", StringArgumentType.greedyString())
                                .executes((ctx) -> {
                                    String function = StringArgumentType.getString(ctx, "function");
                                    String name = StringArgumentType.getString(ctx, "name");
                                   saveNewCommand(name, function);
                                    CalcMessageBuilder messageBuilder = new CalcMessageBuilder("Added "+name);
                                    CalcCommand.sendMessage(ctx.getSource(), messageBuilder);

                                    return 0;
                                }))))
                                .then(ClientCommandManager.literal("list").executes(ctx -> {
                                    JsonObject fs = getFunctions();
                                    String m = fs.entrySet().stream().map(entry -> entry.getKey() + ": " + entry.getValue().getAsString()).collect(Collectors.joining("\n"));
                                    CalcMessageBuilder messageBuilder = new CalcMessageBuilder(m);
                                    CalcCommand.sendMessage(ctx.getSource(), messageBuilder);
                                    return 0;
                                }))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    deleteCommand(name);
                                    CalcMessageBuilder messageBuilder = new CalcMessageBuilder("Removed "+name);
                                    CalcCommand.sendMessage(ctx.getSource(), messageBuilder);
                                    return 0;
                                })))
                                .then(ClientCommandManager.literal("run")
                                        .then(ClientCommandManager.argument("function", StringArgumentType.greedyString())
                                                .suggests(new CCustomFunctionProvider())
                                                .executes((ctx) -> {
                                                    String eqn = StringArgumentType.getString(ctx, "function");
                                                    double result = CalcCommand.getParsedExpression(ctx.getSource().getPlayer().getBlockPos(), eqn);
                                                    CalcMessageBuilder messageBuilder = new CalcMessageBuilder(CalcMessageBuilder.MessageType.BASIC, new String[] {eqn}, new String[] {String.valueOf(result)});
                                                    CalcCommand.sendMessage(ctx.getSource(), messageBuilder);
                                                    return 0;
                                                })))

                                
                                );

        
        return command;
    }

    public static LiteralArgumentBuilder<ServerCommandSource> registerServer(LiteralArgumentBuilder<ServerCommandSource> command) {
        command = command.then(CommandManager.literal("custom")
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .then(CommandManager.argument("function", StringArgumentType.greedyString())
                                        .executes((ctx) -> {
                                            String function = StringArgumentType.getString(ctx, "function");
                                            String name = StringArgumentType.getString(ctx, "name");
                                            saveNewCommand(name, function);
                                            CalcMessageBuilder messageBuilder = new CalcMessageBuilder("Added "+name);
                                            CalcCommand.sendMessageServer(ctx.getSource(), messageBuilder);

                                            return 0;
                                        }))))
                .then(CommandManager.literal("list").executes(ctx -> {
                    JsonObject fs = getFunctions();
                    String m = fs.entrySet().stream().map(entry -> entry.getKey() + ": " + entry.getValue().getAsString()).collect(Collectors.joining("\n"));
                    CalcMessageBuilder messageBuilder = new CalcMessageBuilder(m);
                    CalcCommand.sendMessageServer(ctx.getSource(), messageBuilder);
                    return 0;
                }))
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    deleteCommand(name);
                                    CalcMessageBuilder messageBuilder = new CalcMessageBuilder("Removed "+name);
                                    CalcCommand.sendMessageServer(ctx.getSource(), messageBuilder);
                                    return 0;
                                })))
                .then(CommandManager.literal("run")
                        .then(CommandManager.argument("function", StringArgumentType.greedyString())
                                .suggests(new CustomFunctionProvider())
                                .executes((ctx) -> {
                                    String eqn = StringArgumentType.getString(ctx, "function");
                                    double result = CalcCommand.getParsedExpression(ctx.getSource().getPlayer().getBlockPos(), eqn);
                                    CalcMessageBuilder messageBuilder = new CalcMessageBuilder(CalcMessageBuilder.MessageType.BASIC, new String[] {eqn}, new String[] {String.valueOf(result)});
                                    CalcCommand.sendMessageServer(ctx.getSource(), messageBuilder);
                                    return 0;
                                }))


        ));


        return command;
    }

    public static String evaluateFunction(String eqn, Map<String, String> variables) {
        
        for (var eqnVar : variables.keySet()) {
            if (variables.containsKey(eqnVar)) {
                eqn = eqn.replace("["+eqnVar+"]", variables.get(eqnVar));
            }
        }
        return eqn;
    }


    public static ArrayList<String> parseEquationVariables(String input) {
        String patternString = "\\[([^\\]]+)\\]";//"\\[[^\\]]+\\]";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(input);
        StringBuilder commandJson = new StringBuilder("{\n\"variables\": [\n");
        ArrayList<String> variables = new ArrayList<>();
        while (matcher.find()) {
            String group = matcher.group();
            group = group.substring(1, group.length()-1);
          //  commandJson.append("\"").append(group).append("\",\n");
            variables.add(group);
        }
        commandJson.append("],\n\"equation\": \"").append(input).append("\"\n}");
      //  CalcMod.LOGGER.info(commandJson.toString());
        return variables;
    }

    public static JsonObject readJson() {
         try (BufferedReader reader = new BufferedReader(new FileReader(commandFile))) {
            JsonObject tempJson;
            try {
                tempJson = JsonParser.parseString(reader.lines().collect(Collectors.joining("\n"))).getAsJsonObject();
            } catch (Exception ignored) {
                tempJson = new JsonObject();
            }
            JsonObject json = tempJson; // annoying lambda requirement
            return json;
        } catch (Exception ignored) { return new JsonObject();}

    }


    public static JsonObject getFunctions() {
        JsonObject json = readJson();
        if (!json.has("functions")) {
            json = new JsonObject();
            json.add("functions", new JsonObject());
        }
        return json.getAsJsonObject("functions");
    }

    public static ArrayList<String> getParsedFunctions() {
        ArrayList<String> parsedFuncs = new ArrayList<>();
        JsonObject funcs = getFunctions();
        for (String f : funcs.keySet()) {
            String func = funcs.get(f).getAsString();
            ArrayList<String> vars = parseEquationVariables(func);
            String combinedVars = String.join(", ", vars);
            parsedFuncs.add(f+"("+combinedVars+") = "+func.replace("[", "").replace("]", ""));
          //  CalcMod.LOGGER.info("Parsed Func "+f+"("+combinedVars+") = "+func.replace("[", "").replace("]", ""));
        }
        return parsedFuncs;
    }

    public static void saveNewCommand(String name, String eqn) {
        JsonObject json = readJson();
        if (!json.has("functions")) {
            json = new JsonObject();
            json.add("functions", new JsonObject());
        }
        json.getAsJsonObject("functions").addProperty(name, eqn);
        saveCommandsFile(json);

    }

    public static void deleteCommand(String name) {
        JsonObject json = readJson();
        json.getAsJsonObject("functions").remove(name);
        saveCommandsFile(json);
    }
    public static void saveCommandsFile(JsonObject json) {
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File dir = new File(".", "config");
        if ((dir.exists() && dir.isDirectory() || dir.mkdirs()) && !commandFile.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                commandFile.createNewFile();
                
            } catch (IOException ignored) {}
        }
         try {
        FileWriter writer = new FileWriter(commandFile);
                writer.write(gson.toJson(json));
                writer.close();
                  } catch (IOException ignored) {}
    }
}
