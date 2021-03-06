package dev.skyblock.config.type;

import com.google.common.collect.Maps;
import dev.skyblock.config.ConfigType;
import dev.skyblock.config.LoadableConfig;
import dev.skyblock.config.representer.YamlObjectRepresenter;
import org.bukkit.Bukkit;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

@NotThreadSafe
public abstract class YamlConfig<T extends YamlConfig> implements LoadableConfig<T> {

    private static transient final YamlObjectRepresenter REPRESENTER = new YamlObjectRepresenter();
    private static transient final DumperOptions DUMPER_OPTIONS = new DumperOptions();

    static {
        DUMPER_OPTIONS.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        DUMPER_OPTIONS.setPrettyFlow(true);
    }

    /**
     * Class of the configuration (for serialisation).
     */
    private transient final Class<T> clazz;

    /**
     * Logger to log to.
     */
    private transient final Logger logger;

    /**
     * Backing yaml to load to / from.
     */
    private transient final Yaml backingYaml;

    /**
     * Represents a configuration file.
     *
     * @param clazz Class of the configuration.
     */
    public YamlConfig(Class<T> clazz) {
        this.clazz = clazz;
        this.logger = Bukkit.getServer().getLogger();

        this.backingYaml = new Yaml(new Constructor(this.clazz), REPRESENTER, DUMPER_OPTIONS);
        this.backingYaml.setBeanAccess(BeanAccess.FIELD);

        for (Field field : this.clazz.getDeclaredFields()) {
            field.setAccessible(true);

            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            if (!field.getType().isEnum()) {
                continue;
            }

            this.backingYaml.addTypeDescription(new TypeDescription(field.getType(), field.getName()));
        }
    }

    /**
     * Loads the configuration from disk and returns its instantiated object.
     *
     * @return The config instance.
     */
    @Override
    @SuppressWarnings("unchecked")
    public T load() {
        try {
            this.logger.info("Attempting to load config, " + this.getClass().getSimpleName() + "..");
            return this.backingYaml.loadAs(new FileInputStream(this.getPath().toFile()), this.clazz);
        } catch (Exception e) {
            if (e instanceof FileNotFoundException) {
                this.logger.warning("Could not find, " + this.getPath().toFile().getName() + ", creating one now..");
            } else {
                e.printStackTrace();
            }

            T config = this.getDefaultConfig();

            config.save();

            return config;
        }
    }

    /**
     * @return The type of config this represents on disk.
     */
    @Override
    public ConfigType getType() {
        return ConfigType.YAML;
    }

    /**
     * @return The path that this config should be saved and loaded from.
     */
    @Override
    public abstract Path getPath();

    /**
     * @return Default config to write if none can be found.
     */
    @Override
    public abstract T getDefaultConfig();

    @Override
    public void save() {
        try {
            if (!Files.exists(this.getPath())) {
                Files.createFile(this.getPath());
            }

            Map<String, Object> tags = Maps.newHashMap();

            for (Field field : this.clazz.getDeclaredFields()) {
                field.setAccessible(true);

                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                try {
                    if (field.getClass().isEnum()) {
                        tags.put(field.getName(), ((Enum<?>) field.get(this)).name());
                        continue;
                    }

                    tags.put(field.getName(), field.get(this));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            FileWriter writer = new FileWriter(this.getPath().toFile());

            this.backingYaml.dump(tags, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
