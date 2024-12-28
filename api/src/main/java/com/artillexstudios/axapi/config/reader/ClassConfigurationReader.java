package com.artillexstudios.axapi.config.reader;

import com.artillexstudios.axapi.config.YamlConstructor;
import com.artillexstudios.axapi.config.adapters.TypeAdapterHolder;
import com.artillexstudios.axapi.config.annotation.Comment;
import com.artillexstudios.axapi.config.annotation.ConfigurationPart;
import com.artillexstudios.axapi.config.annotation.Ignored;
import com.artillexstudios.axapi.config.annotation.Named;
import com.artillexstudios.axapi.config.annotation.PostProcess;
import com.artillexstudios.axapi.config.annotation.Serializable;
import com.artillexstudios.axapi.config.renamer.KeyRenamer;
import it.unimi.dsi.fastutil.Pair;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.nodes.AnchorNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// A configuration reader that only cares about the fields of the class
public final class ClassConfigurationReader implements Handler {
    private final TypeAdapterHolder holder;
    private final YamlConstructor constructor;
    private final KeyRenamer renamer;
    private final Class<?> clazz;
    private final Yaml yaml;

    public ClassConfigurationReader(Yaml yaml, YamlConstructor constructor, TypeAdapterHolder holder, KeyRenamer renamer, Class<?> clazz) {
        this.holder = holder;
        this.constructor = constructor;
        this.renamer = renamer;
        this.clazz = clazz;
        this.yaml = yaml;
    }

    @Override
    public Pair<Map<String, Object>, Map<String, Comment>> read(InputStream stream) {
        Map<String, Object> map = this.yaml.load(stream);
        LinkedHashMap<String, Object> ordered = new LinkedHashMap<>();
        Map<String, Comment> comments = new HashMap<>();
        map = map == null ? new HashMap<>() : map;
        try {
            this.readClass(map, ordered, this.clazz);
            this.readComments("", comments, this.clazz);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        return Pair.of(ordered, comments);
    }

    @Override
    public String write(Map<String, Object> contents, Map<String, Comment> comments) {
        StringWriter writer = new StringWriter();
        MappingNode node = this.map(contents);
        this.writeComments("", comments, node);
        this.yaml.serialize(node, writer);
        return writer.toString();
    }

    private MappingNode map(Map<String, Object> map) {
        List<NodeTuple> nodes = new ArrayList<>();
        Node key;
        Node value;
        for (Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator(); iterator.hasNext(); nodes.add(new NodeTuple(key, value))) {
            Map.Entry<String, Object> entry = iterator.next();
            key = this.yaml.represent(entry.getKey());
            if (entry.getValue() instanceof Map<?, ?> m) {
                value = this.map((Map<String, Object>) m);
            } else {
                value = this.yaml.represent(this.holder.serialize(entry.getValue(), entry.getValue().getClass()));
            }
        }

        return new MappingNode(Tag.MAP, nodes, DumperOptions.FlowStyle.BLOCK);
    }

    private void writeComments(String path, Map<String, Comment> comments, MappingNode node) {
        for (NodeTuple nodeTuple : node.getValue()) {
            Node key = nodeTuple.getKeyNode();
            String keyString = String.valueOf(this.constructor.construct(key));
            Node value;

            for (value = nodeTuple.getValueNode(); value instanceof AnchorNode anchorNode; value = anchorNode.getRealNode()) {

            }

            String path1 = path.isEmpty() ? keyString : path + "." + keyString;
            if (value instanceof MappingNode mappingNode) {
                this.writeComments(path1, comments, mappingNode);
            } else {
                Comment comment = comments.get(path1);
                if (comment != null) {
                    List<CommentLine> lines = new ArrayList<>();
                    String[] split = comment.value().split("\n");
                    for (String string : split) {
                        lines.add(new CommentLine(null, null, string, comment.type() == Comment.CommentType.BLOCK ? CommentType.BLOCK : CommentType.BLOCK));
                    }
                    if (comment.type() == Comment.CommentType.BLOCK) {
                        key.setBlockComments(lines);
                    } else {
                        key.setInLineComments(lines);
                    }
                }
            }
        }


    }

    private void readComments(String path, Map<String, Comment> comments, Class<?> clazz) {
        for (Field field : clazz.getFields()) {
            if (Modifier.isFinal(field.getModifiers()) || clazz.isAnnotationPresent(Ignored.class)) {
                continue;
            }


            Named named = field.getAnnotation(Named.class);
            Type type = field.getGenericType();
            String name = named != null ? named.value() : this.renamer.rename(field.getName());
            Comment comment = field.getAnnotation(Comment.class);
            String path1 = path.isEmpty() ? name : path + "." + name;
            if (comment != null) {
                comments.put(path1, comment);
            }

            if (type instanceof Class<?> cl && cl.isAnnotationPresent(Serializable.class)) {
                this.readComments(path1, comments, cl);
            }
        }

        for (Class<?> subClass : clazz.getClasses()) {
            if (subClass.isAnnotationPresent(Ignored.class) || subClass.isAnnotationPresent(Serializable.class) || ConfigurationPart.class.isAssignableFrom(subClass)) {
                continue;
            }

            Named named = subClass.getAnnotation(Named.class);
            String name = named == null ? this.renamer.rename(subClass.getSimpleName()) : named.value();
            Comment comment = subClass.getAnnotation(Comment.class);
            String key = path.isEmpty() ? name : path + "." + name;
            if (comment != null) {
                comments.put(key, comment);
            }
            this.readComments(key, comments, subClass);
        }
    }

    private void readClass(Map<String, Object> from, LinkedHashMap<String, Object> to, Class<?> clazz) throws IllegalAccessException {
        for (Field field : clazz.getFields()) {
            if (Modifier.isFinal(field.getModifiers()) || field.isAnnotationPresent(Ignored.class) || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            Named named = field.getAnnotation(Named.class);
            Type type = field.getGenericType();
            String name = named != null ? named.value() : this.renamer.rename(field.getName());
            Object found = from.get(name);
            if (found == null) {
                found = field.get(null);
            } else {
                found = this.holder.deserialize(found, type);
            }

            to.put(name, found);
            field.set(null, found);
        }

        for (Method method : clazz.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !method.isAnnotationPresent(PostProcess.class)) {
                continue;
            }

            try {
                method.invoke(null);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        for (Class<?> subClass : clazz.getClasses()) {
            if (subClass.isAnnotationPresent(Ignored.class) || subClass.isAnnotationPresent(Serializable.class) || ConfigurationPart.class.isAssignableFrom(subClass)) {
                continue;
            }

            Named named = subClass.getAnnotation(Named.class);
            String name = named == null ? this.renamer.rename(subClass.getSimpleName()) : named.value();
            Map<String, Object> newContents = (Map<String, Object>) from.getOrDefault(name, new LinkedHashMap<>());
            to.put(name, newContents);
            this.readClass(newContents, to, subClass);
        }
    }
}
