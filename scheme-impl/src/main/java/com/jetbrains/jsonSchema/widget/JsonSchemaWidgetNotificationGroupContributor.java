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
package com.jetbrains.jsonSchema.widget;

import consulo.annotation.component.ExtensionImpl;
import consulo.json.localize.JsonLocalize;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationGroupContributor;

import java.util.function.Consumer;

@ExtensionImpl
public class JsonSchemaWidgetNotificationGroupContributor implements NotificationGroupContributor {
    public static final NotificationGroup CONFLICT_GROUP =
        NotificationGroup.balloonGroup("JSON Schema Conflicts", JsonLocalize.schemaWidgetConflictPopupTitle());

    @Override
    public void contribute(Consumer<NotificationGroup> registrator) {
        registrator.accept(CONFLICT_GROUP);
    }
}
