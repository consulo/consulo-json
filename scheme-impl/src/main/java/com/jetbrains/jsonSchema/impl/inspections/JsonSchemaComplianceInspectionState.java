/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl.inspections;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-26
 */
public class JsonSchemaComplianceInspectionState implements InspectionToolState<JsonSchemaComplianceInspectionState> {
    public boolean myCaseInsensitiveEnum = false;

    @Override
    public @Nullable UnnamedConfigurable createConfigurable() {
        ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
        builder.checkBox(
            JsonLocalize.jsonSchemaInspectionCaseInsensitiveEnum(),
            () -> myCaseInsensitiveEnum,
            value -> myCaseInsensitiveEnum = value
        );
        return builder.buildUnnamed();
    }

    @Override
    public JsonSchemaComplianceInspectionState getState() {
        return this;
    }

    @Override
    public void loadState(JsonSchemaComplianceInspectionState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
