package com.taobao.arthas.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.security.CodeSource;
import java.util.Map;
import java.util.Properties;

import com.alibaba.arthas.deps.org.slf4j.Logger;
import com.alibaba.arthas.deps.org.slf4j.LoggerFactory;
import com.taobao.arthas.common.FeatureCodec;
import com.taobao.arthas.plugin.PluginException;
import com.taobao.arthas.plugin.PluginManager;

/**
 *
 * @author hengyunabc 2019-03-01
 *
 */
public class AgentBootstrap2 {
    private static final String defaultLoggerConfigurationFileProperty = "arthas.logback.configurationFile";
    private static final String defaultLoggerConfigurationFile = "logback-arthas.xml";

    private static volatile AgentBootstrap2 instance;
    private final static FeatureCodec codec = new FeatureCodec(';', '=');

    private PluginManager pluginManager;

    private Logger logger;

    public static void premain(String args, Instrumentation inst) {
        main(true, args, inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        main(args, inst);
    }

    private static synchronized void main(final String args, final Instrumentation inst) {
        main(false, args, inst);
    }

    private static synchronized void main(boolean premain, final String args, final Instrumentation inst) {
        if (instance == null) {
            synchronized (AgentBootstrap2.class) {
                if (instance == null) {
                    AgentBootstrap2 temp = new AgentBootstrap2();
                    temp.init(premain, args, inst);
                    instance = temp;
                }
            }
        }
    }

    private void initLogger() {
        String arthasLoggerConfiguration = System.getProperty(defaultLoggerConfigurationFileProperty);
        if (arthasLoggerConfiguration == null || arthasLoggerConfiguration.trim().isEmpty()) {
            System.setProperty(defaultLoggerConfigurationFileProperty, defaultLoggerConfigurationFile);
        }
        if (logger == null) {
            logger = LoggerFactory.getLogger("arthas");
        }

    }

    private void init(boolean premain, final String args, final Instrumentation inst) {
        initLogger();

        Map<String, String> map = codec.toMap(args);

        String arthasHome = map.get("arthas.home");

        if (arthasHome == null) {
            CodeSource codeSource = AgentBootstrap2.class.getProtectionDomain().getCodeSource();
            URL agentJarLocation = codeSource.getLocation();
            arthasHome = new File(agentJarLocation.getFile()).getParent();
            map.put("arthas.home", arthasHome);
        }

        logger.info("arthas home: " + map.get("arthas.home"));

        Properties properties = new Properties();
        properties.putAll(map);

        logger.debug("PluginManager properties: {}", properties);
        pluginManager = new PluginManager(inst, properties);
        try {

            pluginManager.scanPlugins(new File(arthasHome, "plugins"));

            pluginManager.initPlugins();

            pluginManager.startPlugins();

        } catch (PluginException e) {
            logger.error("PluginManager error", e);
            e.printStackTrace();
        }
    }

}
