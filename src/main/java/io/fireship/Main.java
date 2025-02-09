package io.fireship;

import io.fireship.commands.CommandEnum;
import io.fireship.events.ReadyListener;
import io.fireship.events.RoleSelect;
import io.fireship.events.SlashCommand;
import io.fireship.model.Option;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.slf4j.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Main {
    public static Main HELPBOT;
    public Properties appProperties = new Properties();
    public Logger logger;
    public JDA jda;
    public Javalin http;
    public boolean isProduction;
    public Map<String, String> config = new HashMap<>();

    public static void main(String[] args) throws IOException {

        //Create instance of the app to reference it in other locations
        HELPBOT = new Main();
        HELPBOT.logger = org.slf4j.LoggerFactory.getLogger(Main.class);
        HELPBOT.logger.info("Fireship Helpbot Loading...");

        //check if we are running in production
        HELPBOT.isProduction = HELPBOT.productionCheck();

        HELPBOT.initProperties();

        //Load configuration file and grab the bot token
        String botToken = HELPBOT.getBotToken();
        if (botToken == null) {
            HELPBOT.logger.error("Bot token not found");
            return;
        }
        HELPBOT.initBot(botToken);
        HELPBOT.registerCommands();
        try {
            if (HELPBOT.isProduction) HELPBOT.startServer(Integer.parseInt(args[0]));
        } catch (Exception e) {
            HELPBOT.logger.error("Please provide a valid port number");
        }
    }

    void startServer(int port) {
        http = Javalin.create(javalinConfig ->
            javalinConfig.addStaticFiles( staticFiles -> {
                staticFiles.location = Location.CLASSPATH;
                staticFiles.directory = "/static";
                staticFiles.hostedPath = "/";
            })
        ).start(port);
    }

    boolean productionCheck() {
        String token = System.getenv("token");
        return token != null;
    }

    String getBotToken() {
        return HELPBOT.config.get("token");
    }

    //load app.properties from the java resources directory
    void initProperties() throws IOException {
        if(HELPBOT.isProduction) {
            //load from env
            HELPBOT.config.put("token", System.getenv("token"));
        } else {
            HELPBOT.logger.info("Loading app configuration...");
            InputStream str = HELPBOT.getClass().getResourceAsStream("/app.properties");
            appProperties.load(str);
            for(String key : appProperties.stringPropertyNames()) {
                HELPBOT.config.put(key, appProperties.getProperty(key));
            }
            //loop through config and print it out
            for(Map.Entry<String, String> entry : HELPBOT.config.entrySet()) {
                HELPBOT.logger.warn(entry.getKey() + " = " + entry.getValue());
            }
        }
    }

    void initBot(String token) {
        HELPBOT.logger.info("Initializing bot...");
        //build the JDA instance of the bot and store it in a global variable
        jda = JDABuilder.createDefault(token)
                .setActivity(Activity.watching("Fireship.io"))
                .addEventListeners(new ReadyListener())
                .addEventListeners(new SlashCommand())
                .addEventListeners(new RoleSelect())
                .build();
    }

    //Loop through commands enum and register them with discord
    void registerCommands() {
        HELPBOT.logger.info("Registering commands...");
        CommandListUpdateAction commands = jda.updateCommands();
        Arrays.asList(CommandEnum.values()).forEach(commandData -> {
            HELPBOT.logger.info(" - Registering " + commandData.getName());
            SlashCommandData command = Commands.slash(commandData.getName(), commandData.getDescription());

            for (int i = 0; i < commandData.getOptions().size(); i++) {
                Option option = commandData.getOptions().get(i);
                HELPBOT.logger.info(" - - Adding option " + option.getName());
                command.addOption(OptionType.STRING, option.getName(), option.getDescription(), option.isRequired());
            }
            commands.addCommands(command);
        });
        commands.queue();
    }
}