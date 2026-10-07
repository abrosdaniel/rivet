# Оставшиеся русские литералы клиентских экранов

Инвентаризация после A8, 2026-10-07. Общие действия UiActions используют
ключи RU/EN; их подписи проверены в настоящем клиенте.

Полный список кандидатов: UI_LOCALIZATION_INVENTORY.csv. Поиск строковых
литералов — не классификация мёртвого кода. В список также попали
значения домена, примеры и названия серверных разделов. Перед переводом
отделять пользовательские подписи от протокольных значений и данных сервера.
Ники, содержимое объявлений, конфиг владельца и исторические данные не переводить.

Следующий отдельный проход локализации: настройки, задачи и формы, затем
сообщество/профиль, затем администрирование. Не менять компоновку или
сценарии ради перевода. Полная локализация не заявляется выполненной.

| Файл | Литералов-кандидатов |
| --- | ---: |
| `rivet/mod/src/main/java/dev/abros/rivet/client/CommunityScreen.java` | 177 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/TaskScreen.java` | 170 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ServerMenuScreen.java` | 129 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/CommunityForm.java` | 67 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/GroupsSection.java` | 55 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/AccessibilityScreen.java` | 49 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/SparkDiagnosticsScreen.java` | 49 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/CommunityTools.java` | 48 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/HudSettingsScreen.java` | 47 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/SettingsCatalog.java` | 46 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/AdminDashboardScreen.java` | 40 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/AuthScreen.java` | 39 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/SkinsScreen.java` | 39 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/EventsSection.java` | 38 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ReportQueueScreen.java` | 37 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/AuthAccountScreen.java` | 36 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ModerationVoteScreen.java` | 36 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/SocialSettingsScreen.java` | 35 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ReportManagementScreen.java` | 33 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/PlacesScreen.java` | 32 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/PlayerActionsScreen.java` | 30 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ServerPackClient.java` | 30 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/CommunityPreferences.java` | 28 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/UiSearchToolbar.java` | 27 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/FeatureListScreen.java` | 26 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/RivetHud.java` | 25 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ChatHints.java` | 22 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/TaskResourcesScreen.java` | 22 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/BoardSection.java` | 21 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/CommunityCard.java` | 20 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/HudRenderer.java` | 20 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/GlobalSearchScreen.java` | 18 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/PlasmoVoiceClientAdapter.java` | 17 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/RolePreviewScreen.java` | 17 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/DateTimeScreen.java` | 16 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/LocationEditor.java` | 16 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/NotificationPopup.java` | 16 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/AuthClient.java` | 15 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/CoreVersionsPopup.java` | 15 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/LocationActions.java` | 15 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/MapSharingScreen.java` | 15 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ReportScreen.java` | 15 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/UiSettingsShell.java` | 15 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/PlayerAdministrationScreen.java` | 14 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ScheduledAnnouncementsScreen.java` | 14 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/PersonalProfileScreen.java` | 13 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/PrivacyScreen.java` | 13 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ReportBulkScreen.java` | 12 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ServerMenuClient.java` | 12 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/TaskEditScreen.java` | 12 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/PlayerStatisticsText.java` | 11 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ReportDetailScreen.java` | 11 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ServerSettingsReviewScreen.java` | 11 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/TaskPlanning.java` | 11 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/TaskStockSignScreen.java` | 11 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/IdeasSection.java` | 9 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/TaskArchiveScreen.java` | 9 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/CompatibilityClient.java` | 8 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/HudProfile.java` | 8 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/HudSettings.java` | 8 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/NavigationSettingsScreen.java` | 8 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ProfilePanel.java` | 8 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/RivetTab.java` | 8 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/HomeWidgetsScreen.java` | 7 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/HudInteractionScreen.java` | 7 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/TaskMemberPicker.java` | 7 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/UiSettingsPreview.java` | 7 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ChatChannelIndicator.java` | 6 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/DirectionCue.java` | 6 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/FollowingScreen.java` | 6 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ChangePreviewScreen.java` | 5 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/HomeLayout.java` | 5 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/SkinClient.java` | 5 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/XaeroMapBridge.java` | 5 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/CoordinateLinkTooltip.java` | 4 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ItemPickerScreen.java` | 4 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/MemberPickerScreen.java` | 4 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/MenuSidebar.java` | 4 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/QuickSkinsScreen.java` | 4 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ServerPackScreen.java` | 4 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/SettingsSearchScreen.java` | 4 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/UiTextForm.java` | 4 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/UiThemePicker.java` | 4 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/XaeroBridge.java` | 4 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ClientCompatibilityRegistry.java` | 3 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/HudOrderScreen.java` | 3 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/NotificationRow.java` | 3 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/PasswordVisibilityButton.java` | 3 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/PollsSection.java` | 3 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/StockHologramRenderer.java` | 3 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/UiConfirmDialog.java` | 3 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/UiFormDraft.java` | 3 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/ClientChat.java` | 2 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/PlayerRow.java` | 2 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/UiSkinCard.java` | 2 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/MapLayerClient.java` | 1 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/PlayerText.java` | 1 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/RestartScreen.java` | 1 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/UiTabs.java` | 1 |
| `rivet/mod/src/main/java/dev/abros/rivet/client/VoiceDiagnosticsScreen.java` | 1 |
