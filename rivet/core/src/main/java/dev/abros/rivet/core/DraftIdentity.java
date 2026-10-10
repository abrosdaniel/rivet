package dev.abros.rivet.core;

import com.google.gson.JsonObject;

/** Legacy draft identities are storage keys, never translated presentation text. */
public final class DraftIdentity {
    private DraftIdentity() {}

    public static String playerReport(String name) { return "Игрок: " + name + "\n"; }

    public static String entryReport(String section, String groupsTitle, String title, String id) {
        String label = switch (section) {
            case "board" -> "Доска объявлений";
            case "groups" -> groupsTitle;
            case "events" -> "События";
            case "polls" -> "Голосования";
            case "ideas" -> "Предложения";
            default -> section;
        };
        return label + ": " + title + " [" + id + "]\n";
    }

    public static String formTitle(String operation, String section, JsonObject preset) {
        return switch (operation) {
            case "create" -> switch (section) {
                case "board" -> "Новое объявление";
                case "groups" -> "Новое объединение";
                case "events" -> "Новое событие";
                case "polls" -> "Новое голосование";
                case "ideas" -> "Новое предложение";
                default -> "Новая запись";
            };
            case "edit" -> "Редактировать запись";
            case "location" -> "Место";
            case "hide" -> "Модерация";
            case "respond" -> "Отклик";
            case "respondDecision" -> "Ответ на отклик";
            case "status" -> "Официальный ответ";
            case "reschedule" -> "Перенос события";
            case "cancel" -> "Отмена события";
            case "plusRemoveMember" -> "Исключить участника";
            case "apply" -> "Заявка";
            case "plusItemSave" -> preset.has("itemId") ? "Редактировать" :
                    Json.opt(preset,"kind", "").equals("task") ? "Новая задача" : "Новая метка объединения";
            default -> throw new IllegalArgumentException("Unknown draft operation: " + operation);
        };
    }

    /** Accept older localized values when reopening drafts created before stable option IDs. */
    public static boolean booleanValue(String value) {
        return value.equals("true") || value.equals("Да") || value.equals("Yes");
    }
}
