package com.jnf.harness.infrastructure.adapter.skill;

import com.jnf.harness.domain.skill.ISkillStateStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * 本地文件技能启用态：{@link Properties} 持久化“被禁用的技能名集合”，未记录即启用。
 * <p>键 {@code disabled}，值为逗号分隔技能名；写入先落临时文件再原子替换，避免半截文件。
 */
public class FileSkillStateStore implements ISkillStateStore {

    private static final String KEY_DISABLED = "disabled";

    private final Path file;
    private final Set<String> disabled = new LinkedHashSet<>();
    private boolean loaded;

    public FileSkillStateStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    @Override
    public synchronized boolean isEnabled(String name) {
        ensureLoaded();
        return name == null || !disabled.contains(name);
    }

    @Override
    public synchronized void setEnabled(String name, boolean enabled) {
        if (name == null || name.isBlank()) {
            return;
        }
        ensureLoaded();
        boolean changed = enabled ? disabled.remove(name) : disabled.add(name);
        if (changed) {
            save();
        }
    }

    @Override
    public synchronized void remove(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        ensureLoaded();
        if (disabled.remove(name)) {
            save();
        }
    }

    @Override
    public synchronized Set<String> disabled() {
        ensureLoaded();
        return Collections.unmodifiableSet(new LinkedHashSet<>(disabled));
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.exists(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties props = new Properties();
            props.load(in);
            String value = props.getProperty(KEY_DISABLED, "");
            for (String part : value.split(",")) {
                String name = part.strip();
                if (!name.isEmpty()) {
                    disabled.add(name);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("技能状态读取失败: " + file, e);
        }
    }

    private void save() {
        Path target = file.toAbsolutePath().normalize();
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties props = new Properties();
            props.setProperty(KEY_DISABLED, String.join(",", disabled));
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                props.store(out, null);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("技能状态写入失败: " + file, e);
        }
    }
}
