// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.nestedCompletions;

import com.intellij.json.pointer.JsonPointerPosition;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.ApiStatus;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@ApiStatus.Experimental
public interface NestedCompletionsNodeBuilder {
  /**
   * Constructs a tree node that will not forward nested completions from outside down into it's children
   */
  void isolated(@Nonnull String name, @Nonnull Consumer<NestedCompletionsNodeBuilder> childBuilder);

  /**
   * Similar to isolated, but it matches based on a regex
   * By default, all completions are isolated(".*".toRegex()) {}
   */
  void isolated(@Nonnull Pattern regex, @Nonnull Consumer<NestedCompletionsNodeBuilder> childBuilder);

  /** Constructs a node that allows completions from outside to nest into this node */
  void open(@Nonnull String name, @Nonnull Consumer<NestedCompletionsNodeBuilder> childBuilder);

  default void open(@Nonnull String name) {
    open(name, builder -> {});
  }
}

/**
 * Represents a tree structure of how completions can be nested through a schema.
 *
 * If you request completions in a configuration node that has a corresponding com.jetbrains.jsonSchema.impl.JsonSchemaObject
 * as well as a corresponding NestedCompletionsNode, you will see completions for the entire subtree of the NestedCompletionsNode.
 * (subtrees are not expanded below com.jetbrains.jsonSchema.impl.nestedCompletions.ChildNode.Isolated nodes)
 *
 * See tests for details
 */
@ApiStatus.Experimental
class NestedCompletionsNode {
  private final List<ChildNode> children;

  public NestedCompletionsNode(@Nonnull List<ChildNode> children) {
    this.children = children;
  }

  @Nonnull
  public List<ChildNode> getChildren() {
    return children;
  }

  @Nonnull
  public static NestedCompletionsNode buildNestedCompletionsTree(@Nonnull Consumer<NestedCompletionsNodeBuilder> block) {
    List<ChildNode> children = new ArrayList<>();

    NestedCompletionsNodeBuilder builder = new NestedCompletionsNodeBuilder() {
      @Override
      public void isolated(@Nonnull String name, @Nonnull Consumer<NestedCompletionsNodeBuilder> childBuilder) {
        children.add(new ChildNode.Isolated.NamedNode(name, buildNestedCompletionsTree(childBuilder)));
      }

      @Override
      public void isolated(@Nonnull Pattern regex, @Nonnull Consumer<NestedCompletionsNodeBuilder> childBuilder) {
        children.add(new ChildNode.Isolated.RegexNode(regex, buildNestedCompletionsTree(childBuilder)));
      }

      @Override
      public void open(@Nonnull String name, @Nonnull Consumer<NestedCompletionsNodeBuilder> childBuilder) {
        children.add(new ChildNode.OpenNode(name, buildNestedCompletionsTree(childBuilder)));
      }
    };

    block.accept(builder);
    return new NestedCompletionsNode(children);
  }

  @Nonnull
  public NestedCompletionsNode merge(@Nonnull NestedCompletionsNode other) {
    List<ChildNode> merged = new ArrayList<>(this.children);
    merged.addAll(other.children);
    return new NestedCompletionsNode(merged);
  }

  @Nullable
  public static NestedCompletionsNode navigate(@Nullable NestedCompletionsNode node, @Nonnull JsonPointerPosition jsonPointer) {
    if (node == null) return null;
    return node.navigate(0, toPathItems(jsonPointer));
  }

  @Nonnull
  private static List<String> toPathItems(@Nonnull JsonPointerPosition position) {
    String pointer = position.toJsonPointer();
    if (pointer == null || pointer.equals("/")) {
      return Collections.emptyList();
    }
    String[] parts = pointer.substring(1).split("/");
    return List.of(parts);
  }

  @Nullable
  private NestedCompletionsNode navigate(int index, @Nonnull List<String> steps) {
    if (index >= steps.size()) return this;

    List<ChildNode> matchingNodes = new ArrayList<>();
    for (ChildNode child : children) {
      if (matches(child, steps.get(index))) {
        matchingNodes.add(child);
      }
    }

    ChildNode preferredChild = getPreferredChild(matchingNodes);
    if (preferredChild == null) return null;

    return preferredChild.getNode().navigate(index + 1, steps);
  }

  // some schemas provide both named and regex nodes for the same name
  // we need to prioritize named options over regex options
  @Nullable
  private static ChildNode getPreferredChild(@Nonnull Collection<ChildNode> nodes) {
    for (ChildNode node : nodes) {
      if (node instanceof ChildNode.NamedChildNode) {
        return node;
      }
    }
    return nodes.isEmpty() ? null : nodes.iterator().next();
  }

  private static boolean matches(@Nonnull ChildNode node, @Nonnull String name) {
    if (node instanceof ChildNode.Isolated.RegexNode) {
      return ((ChildNode.Isolated.RegexNode) node).getRegex().matcher(name).matches();
    } else if (node instanceof ChildNode.NamedChildNode) {
      return ((ChildNode.NamedChildNode) node).getName().equals(name);
    }
    return false;
  }
}

interface ChildNode {
  @Nonnull
  NestedCompletionsNode getNode();

  interface NamedChildNode extends ChildNode {
    @Nonnull
    String getName();
  }

  abstract class Isolated implements ChildNode {
    public static class RegexNode extends Isolated {
      private final Pattern regex;
      private final NestedCompletionsNode node;

      public RegexNode(@Nonnull Pattern regex, @Nonnull NestedCompletionsNode node) {
        this.regex = regex;
        this.node = node;
      }

      @Nonnull
      public Pattern getRegex() {
        return regex;
      }

      @Override
      @Nonnull
      public NestedCompletionsNode getNode() {
        return node;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegexNode regexNode = (RegexNode) o;
        return regex.pattern().equals(regexNode.regex.pattern()) && node.equals(regexNode.node);
      }

      @Override
      public int hashCode() {
        int result = regex.pattern().hashCode();
        result = 31 * result + node.hashCode();
        return result;
      }
    }

    public static class NamedNode extends Isolated implements NamedChildNode {
      private final String name;
      private final NestedCompletionsNode node;

      public NamedNode(@Nonnull String name, @Nonnull NestedCompletionsNode node) {
        this.name = name;
        this.node = node;
      }

      @Override
      @Nonnull
      public String getName() {
        return name;
      }

      @Override
      @Nonnull
      public NestedCompletionsNode getNode() {
        return node;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NamedNode namedNode = (NamedNode) o;
        return name.equals(namedNode.name) && node.equals(namedNode.node);
      }

      @Override
      public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + node.hashCode();
        return result;
      }
    }
  }

  class OpenNode implements ChildNode, NamedChildNode {
    private final String name;
    private final NestedCompletionsNode node;

    public OpenNode(@Nonnull String name, @Nonnull NestedCompletionsNode node) {
      this.name = name;
      this.node = node;
    }

    @Override
    @Nonnull
    public String getName() {
      return name;
    }

    @Override
    @Nonnull
    public NestedCompletionsNode getNode() {
      return node;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      OpenNode openNode = (OpenNode) o;
      return name.equals(openNode.name) && node.equals(openNode.node);
    }

    @Override
    public int hashCode() {
      int result = name.hashCode();
      result = 31 * result + node.hashCode();
      return result;
    }
  }
}
