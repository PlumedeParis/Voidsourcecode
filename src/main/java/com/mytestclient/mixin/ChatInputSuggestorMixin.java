package com.mytestclient.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mytestclient.command.CommandManager;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Autocompletion for {@code \} commands in the chat box, using Minecraft's own suggestion popup
 * (Tab / arrow keys work exactly like for vanilla "/" commands).
 */
@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
    @Shadow
    @Final
    TextFieldWidget textField;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    boolean completingSuggestions;

    @Shadow
    private ParseResults<ClientCommandSource> parse;

    @Shadow
    @Final
    private List<OrderedText> messages;

    @Shadow
    public abstract void show(boolean narrateFirstSuggestion);

    @Shadow
    public abstract void clearWindow();

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
    private void mytestclient$suggestClientCommands(CallbackInfo ci) {
        String text = textField.getText();
        if (!text.startsWith(CommandManager.PREFIX)) return;
        ci.cancel();
        parse = null;     // no vanilla command highlighting for our commands
        messages.clear(); // no vanilla "unknown command" errors
        if (completingSuggestions) return; // the user is cycling through suggestions with Tab
        int cursor = textField.getCursor();
        String upToCursor = text.substring(0, Math.min(cursor, text.length()));
        Map.Entry<Integer, List<String>> result = CommandManager.suggest(upToCursor);
        SuggestionsBuilder builder = new SuggestionsBuilder(upToCursor, result.getKey());
        for (String s : result.getValue()) builder.suggest(s);
        Suggestions suggestions = builder.build();
        textField.setSuggestion(null);
        clearWindow();
        pendingSuggestions = CompletableFuture.completedFuture(suggestions);
        if (!suggestions.isEmpty()) show(false);
    }
}
