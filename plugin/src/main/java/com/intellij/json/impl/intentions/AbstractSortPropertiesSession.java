// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.intentions;

import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractSortPropertiesSession<TObj extends PsiElement, TProp extends PsiElement> {

  protected final PsiFile file;
  protected final TextRange selection;
  protected final TObj rootObj;
  protected final Set<TObj> objects;

  public AbstractSortPropertiesSession(TextRange selection, PsiFile file) {
    this.file = file;
    this.selection = selection;
    this.rootObj = findRootObject();
    this.objects = rootObj != null ? collectObjects(rootObj) : Collections.emptySet();
  }

  @Nullable
  public PsiElement getRootElement() {
    return rootObj;
  }

  protected abstract TObj findRootObject();
  protected abstract Set<TObj> collectObjects(TObj rootObj);
  protected abstract List<TProp> getProperties(TObj obj);
  @Nullable
  protected abstract String getPropertyName(TProp prop);
  @Nullable
  protected abstract TObj getParentObject(TObj obj);
  protected abstract void traverseObjects(TObj root, java.util.function.Consumer<TObj> visitor);

  @Nullable
  protected TObj adjustToSelectionContainer(@Nullable TObj initObj) {
    boolean hasSelection = selection.getStartOffset() != selection.getEndOffset();
    if (initObj == null || !hasSelection) return initObj;
    TObj obj = initObj;
    while (obj.getTextRange() != null && !obj.getTextRange().containsRange(selection.getStartOffset(), selection.getEndOffset())) {
      TObj parent = getParentObject(obj);
      if (parent == null) break;
      obj = parent;
    }
    return obj;
  }

  protected Set<TObj> collectIntersectingObjects(TObj rootObj) {
    Set<TObj> result = new LinkedHashSet<>();
    boolean hasSelection = selection.getStartOffset() != selection.getEndOffset();
    if (hasSelection) {
      traverseObjects(rootObj, o -> {
        if (o.getTextRange() != null && o.getTextRange().intersects(selection.getStartOffset(), selection.getEndOffset())) {
          result.add(o);
        }
      });
    }
    result.add(rootObj);
    return result;
  }

  public boolean hasUnsortedObjects() {
    return objects.stream().anyMatch(obj -> !isSorted(obj));
  }

  public void sort() {
    objects.forEach(obj -> {
      if (!isSorted(obj)) {
        cycleSortProperties(obj);
      }
    });
  }

  // Shared implementation
  private boolean isSorted(TObj obj) {
    List<TProp> properties = getProperties(obj);
    for (int i = 0; i < properties.size() - 1; i++) {
      String left = getPropertyName(properties.get(i));
      String right = getPropertyName(properties.get(i + 1));
      if ((left != null ? left : "").compareTo(right != null ? right : "") > 0) {
        return false;
      }
    }
    return true;
  }

  // cycle-sort performs the minimal amount of modifications, which keeps PSI patches small
  private void cycleSortProperties(TObj obj) {
    List<TProp> properties = getProperties(obj);
    int size = properties.size();
    for (int cycleStart = 0; cycleStart < size; cycleStart++) {
      TProp item = properties.get(cycleStart);
      int pos = advance(properties, size, cycleStart, item);
      if (pos == -1) continue;
      if (pos != cycleStart) {
        exchange(properties, pos, cycleStart);
      }
      while (pos != cycleStart) {
        pos = advance(properties, size, cycleStart, properties.get(cycleStart));
        if (pos == -1) break;
        if (pos != cycleStart) {
          exchange(properties, pos, cycleStart);
        }
      }
    }
  }

  private int advance(List<TProp> properties, int size, int cycleStart, TProp item) {
    int pos = cycleStart;
    String itemName = getPropertyName(item);
    if (itemName == null) itemName = "";
    for (int i = cycleStart + 1; i < size; i++) {
      String propName = getPropertyName(properties.get(i));
      if ((propName != null ? propName : "").compareTo(itemName) < 0) pos++;
    }
    if (pos == cycleStart) return -1;
    while (itemName.equals(getPropertyName(properties.get(pos)) != null ? getPropertyName(properties.get(pos)) : "")) pos++;
    return pos;
  }

  @SuppressWarnings("unchecked")
  private void exchange(List<TProp> properties, int pos, int item) {
    TProp propertyAtPos = properties.get(pos);
    TProp itemProperty = properties.get(item);
    PsiElement propertyAtPosParent = propertyAtPos.getParent();
    PsiElement itemPropertyParent = itemProperty.getParent();
    properties.set(pos, (TProp) propertyAtPosParent.addBefore(itemProperty, propertyAtPos));
    properties.set(item, (TProp) itemPropertyParent.addBefore(propertyAtPos, itemProperty));
    propertyAtPos.delete();
    itemProperty.delete();
  }
}
