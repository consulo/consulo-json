package com.jetbrains.jsonSchema.impl.light;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * An interface to encapsulate all schema traversal methods to be able to replace the implementation without changing an existing API
 */
interface RawJsonSchemaNodeAccessor<T> {
  /**
   * Resolve raw schema node from the given schema root by the given node's json pointer
   */
  @Nullable
  T resolveNode(@Nonnull T rootNode, @Nonnull String absoluteNodeJsonPointer);

  /**
   * Resolve raw schema node from the given schema node by the given node's name
   */
  @Nullable
  default T resolveRelativeNode(@Nonnull T node, @Nullable String relativeChildPath) {
    return resolveRelativeNode(node, relativeChildPath);
  }

  @Nullable
  T resolveRelativeNode(@Nonnull T node);

  boolean hasChildNode(@Nonnull T node, @Nonnull String relativeChildPath);

  @Nullable
  default String readTextNodeValue(@Nonnull T node, @Nullable String relativeChildPath) {
    return readTextNodeValue(node, relativeChildPath);
  }

  @Nullable
  String readTextNodeValue(@Nonnull T node);

  @Nullable
  default Boolean readBooleanNodeValue(@Nonnull T node, @Nullable String relativeChildPath) {
    return readBooleanNodeValue(node, relativeChildPath);
  }

  @Nullable
  Boolean readBooleanNodeValue(@Nonnull T node);

  @Nullable
  default Number readNumberNodeValue(@Nonnull T node, @Nullable String relativeChildPath) {
    return readNumberNodeValue(node, relativeChildPath);
  }

  @Nullable
  Number readNumberNodeValue(@Nonnull T node);

  @Nullable
  default String readUntypedNodeValueAsText(@Nonnull T node, @Nullable String relativeChildPath) {
    return readUntypedNodeValueAsText(node, relativeChildPath);
  }

  @Nullable
  String readUntypedNodeValueAsText(@Nonnull T node);

  @Nullable
  default Iterable<String> readNodeKeys(@Nonnull T node, @Nullable String relativeChildPath) {
    return readNodeKeys(node, relativeChildPath);
  }

  @Nullable
  Iterable<String> readNodeKeys(@Nonnull T node);

  @Nullable
  default Iterable<Object> readUntypedNodesCollection(@Nonnull T node, @Nullable String relativeChildPath) {
    return readUntypedNodesCollection(node, relativeChildPath);
  }

  @Nullable
  Iterable<Object> readUntypedNodesCollection(@Nonnull T node);

  @Nullable
  default Iterable<Pair<String, T>> readNodeAsMapEntries(@Nonnull T node, @Nullable String relativeChildPath) {
    return readNodeAsMapEntries(node, relativeChildPath);
  }

  @Nullable
  Iterable<Pair<String, T>> readNodeAsMapEntries(@Nonnull T node);

  @Nullable
  default Iterable<Pair<String, List<String>>> readNodeAsMultiMapEntries(@Nonnull T node, @Nullable String relativeChildPath) {
    return readNodeAsMultiMapEntries(node, relativeChildPath);
  }

  @Nullable
  Iterable<Pair<String, List<String>>> readNodeAsMultiMapEntries(@Nonnull T node);

  class Pair<K, V> {
    private final K first;
    private final V second;

    public Pair(K first, V second) {
      this.first = first;
      this.second = second;
    }

    public K getFirst() {
      return first;
    }

    public V getSecond() {
      return second;
    }
  }
}
