// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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

import com.intellij.json.JsonLanguage;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.extension.JsonWidgetSuppressor;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.impl.JsonSchemaByCommentProvider;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl;
import com.jetbrains.jsonSchema.internal.JsonCachedValues;
import com.jetbrains.jsonSchema.internal.JsonFileResolver;
import com.jetbrains.jsonSchema.internal.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.remote.JsonSchemaRemoteContentService;
import com.jetbrains.jsonSchema.remote.RemoteDownloadFailure;
import com.jetbrains.jsonSchema.remote.RemoteSchemaDownloadListener;
import com.jetbrains.jsonSchema.remote.SchemaOrigin;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.progress.ProgressIndicator;
import consulo.application.util.SynchronizedClearableLazy;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.component.messagebus.MessageBusConnection;
import consulo.dataContext.DataContext;
import consulo.disposer.Disposer;
import consulo.fileEditor.statusBar.EditorBasedStatusBarPopup;
import consulo.json.localize.JsonLocalize;
import consulo.language.Language;
import consulo.language.file.LanguageFileType;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.project.event.DumbModeListener;
import consulo.project.localize.ProjectLocalize;
import consulo.project.ui.notification.NotificationService;
import consulo.project.ui.wm.StatusBarWidget;
import consulo.project.ui.wm.StatusBarWidgetFactory;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.TestOnly;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

final class JsonSchemaStatusWidget extends EditorBasedStatusBarPopup {
    public static final String ID = "JSONSchemaSelector";
    private final SynchronizedClearableLazy<@Nullable JsonSchemaService> myServiceLazy;
    private static final AtomicBoolean myIsNotified = new AtomicBoolean(false);

    private final AtomicReference<@Nullable Pair<VirtualFile, Boolean>> mySuppressInfoRef = new AtomicReference<>();

    private volatile @Nullable Pair<WidgetState, @Nullable VirtualFile> myLastWidgetStateAndFilePair;
    private @Nullable ProgressIndicator myCurrentProgress;

    private JsonSchemaStatusWidget(Project project, StatusBarWidgetFactory factory) {
        super(project, factory, false);

        myServiceLazy = new SynchronizedClearableLazy<>(() -> {
            if (!project.isDisposed()) {
                JsonSchemaService myService = JsonSchemaService.get(project);
                myService.registerRemoteUpdateCallback(myUpdateCallback);
                myService.registerResetAction(myUpdateCallback);
                return myService;
            }
            return null;
        });
    }

    public static JsonSchemaStatusWidget create(Project project, StatusBarWidgetFactory factory) {
        return new JsonSchemaStatusWidget(project, factory);
    }

    private @Nullable JsonSchemaService getService() {
        return myServiceLazy.getValue();
    }

    private final Runnable myUpdateCallback = () -> {
        update();
        myIsNotified.set(false);
    };

    private static final class MyWidgetState extends WidgetState {
        boolean warning = false;
        boolean conflict = false;
        @Nullable JsonSchemaInfoPopupStep popupStep;

        MyWidgetState(LocalizeValue toolTip, LocalizeValue text, boolean actionEnabled) {
            super(toolTip, text, actionEnabled);
        }

        public boolean isWarning() {
            return warning;
        }

        public void setWarning(boolean warning) {
            this.warning = warning;
            this.setIcon(warning ? PlatformIconGroup.generalWarning() : null);
        }

        private void setConflict() {
            this.conflict = true;
        }
    }

    @Contract("_, null -> false")
    public static boolean isAvailableOnFile(Project project, @Nullable VirtualFile file) {
        if (file == null) {
            return false;
        }
        if (!project.getApplication().getExtensionPoint(JsonSchemaEnabler.class)
            .anyMatchSafe(e -> e.isEnabledForFile(file, project) && e.shouldShowSwitcherWidget(file))) {
            return false;
        }
        return true;
    }

    @Override
    public void update(@Nullable Runnable finishUpdate) {
        mySuppressInfoRef.set(null);
        super.update(finishUpdate);
    }

    private static WidgetStatus getWidgetStatus(Project project, VirtualFile file) {
        Application application = project.getApplication();
        if (!application.getExtensionPoint(JsonSchemaEnabler.class)
            .anyMatchSafe(e -> e.isEnabledForFile(file, project) && e.shouldShowSwitcherWidget(file))) {
            return WidgetStatus.DISABLED;
        }
        if (DumbService.getInstance(project).isDumb()) {
            return WidgetStatus.ENABLED;
        }
        if (application.getExtensionPoint(JsonWidgetSuppressor.class).anyMatchSafe(s -> s.isCandidateForSuppress(file, project))) {
            return WidgetStatus.MAYBE_SUPPRESSED;
        }
        return WidgetStatus.ENABLED;
    }

    @Override
    @RequiredReadAction
    protected WidgetState getWidgetState(@Nullable VirtualFile file) {
        Pair<WidgetState, @Nullable VirtualFile> lastStateAndFilePair = myLastWidgetStateAndFilePair;
        WidgetState widgetState = calcWidgetState(file,
            lastStateAndFilePair == null ? null : lastStateAndFilePair.getFirst(),
            lastStateAndFilePair == null ? null : lastStateAndFilePair.getSecond());
        if (file != null && widgetState instanceof MyWidgetState myWidgetState && myWidgetState.popupStep == null) {
            JsonSchemaService service = getService();
            if (service != null) {
                myWidgetState.popupStep = JsonSchemaStatusPopup.createPopupStep(service, getProject(), file, myWidgetState.isWarning());
            }
        }
        myLastWidgetStateAndFilePair = new Pair<>(widgetState, file);
        return widgetState;
    }

    @RequiredReadAction
    private WidgetState calcWidgetState(@Nullable VirtualFile file,
                                        @Nullable WidgetState lastWidgetState,
                                        @Nullable VirtualFile lastFile) {
        Pair<VirtualFile, Boolean> suppressInfo = mySuppressInfoRef.getAndSet(null);
        ProgressIndicator currentProgress = myCurrentProgress;
        if (currentProgress != null && !currentProgress.isCanceled()) {
            currentProgress.cancel();
        }

        if (file == null) {
            return WidgetState.HIDDEN;
        }
        WidgetStatus status = getWidgetStatus(getProject(), file);
        if (status == WidgetStatus.DISABLED) {
            return WidgetState.HIDDEN;
        }

        FileType fileType = file.getFileType();
        Language language = fileType instanceof LanguageFileType languageFileType ? languageFileType.getLanguage() : null;
        boolean isJsonFile = language instanceof JsonLanguage;

        if (DumbService.getInstance(getProject()).isDumb()) {
            return getDumbModeState(isJsonFile);
        }

        if (status == WidgetStatus.MAYBE_SUPPRESSED) {
            if (suppressInfo == null || !Objects.equals(suppressInfo.first, file)) {
                EmptyProgressIndicator progress = new EmptyProgressIndicator();
                myCurrentProgress = progress;
                scheduleSuppressCheck(file, progress);

                // show 'loading' only when switching between files and previous state was not hidden, otherwise the widget will "jump"
                if (!Objects.equals(lastFile, file) && lastWidgetState != null && lastWidgetState != WidgetState.HIDDEN) {
                    return new WidgetState(
                        JsonLocalize.schemaWidgetCheckingStateTooltip(),
                        JsonLocalize.schemaWidgetCheckingStateText(
                            isJsonFile ? JsonLocalize.schemaWidgetPrefixJsonFiles() : JsonLocalize.schemaWidgetPrefixOtherFiles()
                        ),
                        false
                    );
                }
                else {
                    return WidgetState.NO_CHANGE;
                }
            }
            else if (Boolean.TRUE.equals(suppressInfo.second)) {
                return WidgetState.HIDDEN;
            }
        }

        return doGetWidgetState(file, isJsonFile);
    }

    @RequiredReadAction
    private WidgetState doGetWidgetState(VirtualFile file, boolean isJsonFile) {
        JsonSchemaService service = getService();
        JsonSchemaMappingsProjectConfiguration userMappingsConfiguration = JsonSchemaMappingsProjectConfiguration.getInstance(myProject);
        if (service == null || userMappingsConfiguration.isIgnoredFile(file)) {
            return getNoSchemaState();
        }
        Collection<VirtualFile> schemaFiles = service.getSchemaFilesForFile(file);
        if (schemaFiles.isEmpty()) {
            return getPendingOrNoSchemaState(file, isJsonFile);
        }

        if (schemaFiles.size() != 1) {
            List<VirtualFile> userSchemas = new ArrayList<>();
            if (hasConflicts(userSchemas, service, file)) {
                MyWidgetState state = new MyWidgetState(
                    LocalizeValue.ofNullable(createMessage(
                        schemaFiles,
                        service,
                        "<br/>",
                        JsonLocalize.schemaWidgetConflictMessagePrefix().get(),
                        ""
                    )),
                    LocalizeValue.join(
                        LocalizeValue.of(schemaFiles.size() + " "),
                        JsonLocalize.schemaWidgetConflictMessagePostfix()
                    ),
                    true
                );
                state.setWarning(true);
                state.setConflict();
                return state;
            }
            schemaFiles = userSchemas;
            if (schemaFiles.isEmpty()) {
                return getNoSchemaState();
            }
        }

        VirtualFile schemaFile = schemaFiles.iterator().next();
        return stateForSingleSchemaFile(schemaFile, isJsonFile);
    }

    private WidgetState stateForSingleSchemaFile(VirtualFile schemaFile, boolean isJsonFile) {
        LocalizeValue tooltip = isJsonFile ? JsonLocalize.schemaWidgetTooltipJsonFiles() : JsonLocalize.schemaWidgetTooltipOtherFiles();
        LocalizeValue bar = isJsonFile ? JsonLocalize.schemaWidgetPrefixJsonFiles() : JsonLocalize.schemaWidgetPrefixOtherFiles();

        if (!isValidSchemaFile(schemaFile)) {
            MyWidgetState state =
                new MyWidgetState(JsonLocalize.schemaWidgetErrorNotASchema(), JsonLocalize.schemaWidgetErrorLabel(), true);
            state.setWarning(true);
            return state;
        }

        JsonSchemaService service = getService();
        JsonSchemaFileProvider provider = service == null ? null : service.getSchemaProvider(schemaFile);
        if (provider != null) {
            boolean preferRemoteSchemas = JsonSchemaCatalogProjectConfiguration.getInstance(getProject()).isPreferRemoteSchemas();
            String remoteSource = provider.getRemoteSource();
            boolean useRemoteSource = preferRemoteSchemas && remoteSource != null
                && !JsonFileResolver.isSchemaUrl(remoteSource)
                && !remoteSource.endsWith("!");
            String providerName = useRemoteSource ? Objects.requireNonNull(remoteSource) : provider.getPresentableName();
            String shortName = StringUtil.trimEnd(StringUtil.trimEnd(providerName, ".json"), "-schema");
            LocalizeValue name = useRemoteSource
                ? LocalizeValue.of(provider.getPresentableName())
                : (shortName.contains(JsonLocalize.schemaOfVersion("").get())
                ? LocalizeValue.of(shortName)
                : LocalizeValue.join(bar, LocalizeValue.of(shortName)));
            LocalizeValue kind =
                !useRemoteSource && (provider.getSchemaType() == SchemaType.embeddedSchema || provider.getSchemaType() == SchemaType.schema)
                    ? JsonLocalize.schemaWidgetBundledPostfix()
                    : LocalizeValue.empty();
            return new MyWidgetState(LocalizeValue.join(tooltip, LocalizeValue.of(providerName), kind), name, true);
        }

        return new MyWidgetState(
            LocalizeValue.join(tooltip, getSchemaFileDesc(schemaFile)),
            LocalizeValue.join(bar, getPresentableNameForFile(schemaFile)),
            true
        );
    }

    @RequiredReadAction
    WidgetState getPendingOrNoSchemaState(VirtualFile file, boolean isJsonFile) {
        String url = JsonCachedValues.getSchemaUrlFromSchemaProperty(file, getProject());
        if (url == null) {
            url = JsonSchemaByCommentProvider.getCommentSchema(file, getProject());
        }
        if (url == null) {
            JsonSchemaService service = getService();
            if (service instanceof JsonSchemaServiceImpl serviceImpl) {
                url = serviceImpl.getProvidersForFile(file).stream()
                    .map(JsonSchemaFileProvider::getRemoteSource)
                    .filter(Objects::nonNull)
                    .filter(JsonFileResolver::isHttpPath)
                    .findFirst()
                    .orElse(null);
            }
        }
        if (url == null || !JsonFileResolver.isHttpPath(url)) {
            return getNoSchemaState();
        }
        JsonSchemaRemoteContentService remoteContentService = JsonSchemaRemoteContentService.getInstance(getProject());
        if (!remoteContentService.isAllowed()) {
            return getNoSchemaState();
        }
        VirtualFile peeked = remoteContentService.peekCachedFile(url);
        if (peeked != null) {
            return stateForSingleSchemaFile(peeked, isJsonFile);
        }
        if (remoteContentService.hasInFlight(url)) {
            return new MyWidgetState(
                JsonLocalize.schemaWidgetDownloadInProgressTooltip(),
                JsonLocalize.schemaWidgetDownloadInProgressLabel(),
                false
            );
        }
        RemoteDownloadFailure failure = remoteContentService.lastFailure(url);
        if (remoteContentService.isUnavailable(url)
            || failure == RemoteDownloadFailure.Failed
            || failure == RemoteDownloadFailure.Rejected) {
            return new MyWidgetState(
                JsonLocalize.schemaWidgetDownloadFailedTooltip(),
                JsonLocalize.schemaWidgetDownloadFailedLabel(),
                true
            );
        }
        if (JsonSchemaRemoteContentService.isValidUrl(url)) {
            remoteContentService.prefetch(url);
        }
        return new MyWidgetState(
            JsonLocalize.schemaWidgetDownloadInProgressTooltip(),
            JsonLocalize.schemaWidgetDownloadInProgressLabel(),
            false
        );
    }

    @TestOnly
    static final class TestWidgetState {
        final String text;

        private TestWidgetState(@Nullable String text) {
            this.text = text == null ? "" : text;
        }
    }

    @TestOnly
    @RequiredReadAction
    static TestWidgetState pendingStateForTests(Project project, VirtualFile file, boolean isJsonFile) {
        JsonSchemaStatusWidget widget = create(project, new JsonSchemaStatusWidgetFactory());
        try {
            return new TestWidgetState(widget.getPendingOrNoSchemaState(file, isJsonFile).getText());
        }
        finally {
            Disposer.dispose(widget);
        }
    }

    @TestOnly
    @RequiredReadAction
    static TestWidgetState widgetStateForTests(Project project, VirtualFile file, boolean isJsonFile) {
        JsonSchemaStatusWidget widget = create(project, new JsonSchemaStatusWidgetFactory());
        try {
            return new TestWidgetState(widget.doGetWidgetState(file, isJsonFile).getText());
        }
        finally {
            Disposer.dispose(widget);
        }
    }

    private void scheduleSuppressCheck(VirtualFile file, ProgressIndicator globalProgress) {
        Runnable update = () -> {
            if (DumbService.getInstance(getProject()).isDumb()) {
                // Suppress check should be rescheduled when dumb mode ends.
                mySuppressInfoRef.set(null);
            }
            else {
                boolean suppress = getProject().getApplication().getExtensionPoint(JsonWidgetSuppressor.class)
                    .anyMatchSafe(s -> s.suppressSwitcherWidget(file, getProject()));
                mySuppressInfoRef.set(Pair.create(file, suppress));
            }
            super.update(null);
        };

        Application application = getProject().getApplication();
        if (application.isUnitTestMode()) {
            // Give tests a chance to check the widget state before the task is run (see EditorBasedStatusBarPopup#updateInTests())
            application.invokeLater(update);
        }
        else {
            ReadAction
                .nonBlocking(update)
                .expireWith(this)
                .wrapProgress(globalProgress)
                .coalesceBy(this)
                .submit(AppExecutorUtil.getAppExecutorService());
        }
    }

    private static WidgetState getDumbModeState(boolean isJsonFile) {
        return new WidgetState(
            JsonLocalize.schemaWidgetServiceNotAvailableInDumbMode(JsonLocalize.schemaWidgetService()),
            LocalizeValue.join(
                isJsonFile ? JsonLocalize.schemaWidgetPrefixJsonFiles() : JsonLocalize.schemaWidgetPrefixOtherFiles(),
                ProjectLocalize.progressIndexingUpdating()
            ),
            false
        );
    }

    private boolean isValidSchemaFile(@Nullable VirtualFile schemaFile) {
        // to avoid widget blinking we consider currently loaded schema as valid one
        if (schemaFile == null) {
            return false;
        }
        if (schemaFile.getUserData(SchemaOrigin.URL_KEY) != null) {
            return true;
        }
        JsonSchemaService service = getService();
        return service != null && service.isSchemaFile(schemaFile) && service.isApplicableToFile(schemaFile);
    }

    private static @Nullable String extractNpmPackageName(@Nullable String path) {
        if (path == null) {
            return null;
        }
        int idx = path.indexOf("node_modules");
        if (idx != -1) {
            int trimIndex = idx + "node_modules".length() + 1;
            if (trimIndex < path.length()) {
                String packagePath = path.substring(trimIndex);
                idx = StringUtil.indexOfAny(packagePath, "\\/");
                if (idx != -1) {
                    if (packagePath.startsWith("@")) {
                        idx = StringUtil.indexOfAny(packagePath, "\\/", idx + 1, packagePath.length());
                    }
                }

                if (idx != -1) {
                    return packagePath.substring(0, idx);
                }
            }
        }
        return null;
    }

    private static LocalizeValue getPresentableNameForFile(VirtualFile schemaFile) {
        String originUrl = schemaFile.getUserData(SchemaOrigin.URL_KEY);
        if (originUrl != null) {
            return LocalizeValue.of(new JsonSchemaInfo(originUrl).getDescription());
        }

        String nameWithoutExtension = schemaFile.getNameWithoutExtension();
        if (!JsonSchemaInfo.isVeryDumbName(nameWithoutExtension)) {
            return LocalizeValue.of(nameWithoutExtension);
        }

        String path = schemaFile.getPath();

        String npmPackageName = extractNpmPackageName(path);
        return LocalizeValue.of(npmPackageName != null ? npmPackageName : schemaFile.getName());
    }

    private static WidgetState getNoSchemaState() {
        return new MyWidgetState(JsonLocalize.schemaWidgetNoSchemaTooltip(), JsonLocalize.schemaWidgetNoSchemaLabel(), true);
    }

    private static LocalizeValue getSchemaFileDesc(VirtualFile schemaFile) {
        String originUrl = schemaFile.getUserData(SchemaOrigin.URL_KEY);
        if (originUrl != null) {
            return LocalizeValue.of(originUrl);
        }

        String npmPackageName = extractNpmPackageName(schemaFile.getPath());
        if (npmPackageName == null) {
            return LocalizeValue.of(schemaFile.getName());
        }
        return LocalizeValue.join(
            LocalizeValue.of(schemaFile.getName() + " "),
            JsonLocalize.schemaWidgetPackagePostfix(npmPackageName)
        );
    }

    @Override
    @RequiredUIAccess
    protected @Nullable ListPopup createPopup(DataContext context) {
        VirtualFile file = context.getData(VirtualFile.KEY);
        if (file == null) {
            return null;
        }

        Pair<WidgetState, @Nullable VirtualFile> lastWidgetStateAndFilePair = myLastWidgetStateAndFilePair;
        WidgetState lastWidgetState = Pair.getFirst(lastWidgetStateAndFilePair);
        if (lastWidgetState instanceof MyWidgetState myWidgetState && lastWidgetStateAndFilePair != null && file.equals(lastWidgetStateAndFilePair.getSecond())) {
            JsonSchemaInfoPopupStep step = myWidgetState.popupStep;
            if (step != null) {
                return JBPopupFactory.getInstance().createListPopup(step);
            }
        }
        return null;
    }

    @Override
    protected void registerCustomListeners() {
        final class Listener implements DumbModeListener {
            volatile boolean isDumbMode;

            @Override
            public void enteredDumbMode() {
                isDumbMode = true;
                update();
            }

            @Override
            public void exitDumbMode() {
                isDumbMode = false;
                update();
            }
        }

        MessageBusConnection connection = myConnection;
        if (connection == null) {
            return;
        }
        connection.subscribe(DumbModeListener.class, new Listener());
        connection.subscribe(RemoteSchemaDownloadListener.class, url -> update());
    }

    @Override
    protected void handleFileChange(@Nullable VirtualFile file) {
        myIsNotified.set(false);
    }

    @Override
    protected StatusBarWidget createInstance(Project project) {
        return create(project, myFactory);
    }

    @Override
    public void dispose() {
        JsonSchemaService service = myServiceLazy.isInitialized() ? myServiceLazy.getValue() : null;
        if (service != null) {
            service.unregisterRemoteUpdateCallback(myUpdateCallback);
            service.unregisterResetAction(myUpdateCallback);
        }

        super.dispose();
    }

    @SuppressWarnings("SameParameterValue")
    private static @Nullable String createMessage(Collection<? extends VirtualFile> schemaFiles,
                                                  JsonSchemaService jsonSchemaService,
                                                  String separator,
                                                  String prefix,
                                                  String suffix) {
        List<Pair<Boolean, String>> pairList = schemaFiles.stream()
            .map(file -> jsonSchemaService.getSchemaProvider(file))
            .filter(Objects::nonNull)
            .map(provider -> Pair.create(SchemaType.userSchema.equals(provider.getSchemaType()), provider.getName()))
            .toList();

        long numOfSystemSchemas = pairList.stream().filter(pair -> !pair.getFirst()).count();
        // do not report anything if there is only one system schema and one user schema (user overrides schema that we provide)
        if (pairList.size() == 2 && numOfSystemSchemas == 1) {
            return null;
        }

        boolean withTypes = numOfSystemSchemas > 0;
        return pairList.stream().map(pair -> formatName(withTypes, pair)).collect(Collectors.joining(separator, prefix, suffix));
    }

    private static String formatName(boolean withTypes, Pair<Boolean, String> pair) {
        return "&nbsp;&nbsp;- " + (withTypes
            ? String.format("%s schema '%s'", Boolean.TRUE.equals(pair.getFirst()) ? "user" : "system", pair.getSecond())
            : pair.getSecond());
    }

    private static boolean hasConflicts(Collection<VirtualFile> files, JsonSchemaService service, VirtualFile file) {
        List<JsonSchemaFileProvider> providers = ((JsonSchemaServiceImpl) service).getProvidersForFile(file);
        for (JsonSchemaFileProvider provider : providers) {
            if (provider.getSchemaType() != SchemaType.userSchema) {
                continue;
            }
            VirtualFile schemaFile = provider.getSchemaFile();
            String remoteSource = provider.getRemoteSource();
            if (schemaFile == null && remoteSource != null) {
                schemaFile = JsonSchemaRemoteContentService.getInstance(service.getProject()).getCachedFile(remoteSource);
            }
            if (schemaFile != null) {
                files.add(schemaFile);
            }
        }
        return files.size() > 1;
    }

    @Override
    @RequiredUIAccess
    protected void afterVisibleUpdate(WidgetState state) {
        if (!(state instanceof MyWidgetState myWidgetState) || !myWidgetState.conflict) {
            myIsNotified.set(false);
            return;
        }
        if (myIsNotified.get()) {
            return;
        }

        myIsNotified.set(true);
        NotificationService.getInstance()
            .newError(JsonSchemaWidgetNotificationGroupContributor.CONFLICT_GROUP)
            .title(JsonLocalize.schemaWidgetConflictPopupTitle())
            .content(state.getToolTip())
            .notify(getProject());
    }

    private enum WidgetStatus {
        ENABLED, DISABLED, MAYBE_SUPPRESSED
    }
}
