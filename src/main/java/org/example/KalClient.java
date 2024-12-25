package org.example;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.voice.AudioProvider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class KalClient {

    private static final int MAX_SEARCH_RESULTS = 9;

    private final Map<String, Command> commands = new HashMap<>();
    private final AudioProvider audioProvider;
    private final AudioPlayerManager playerManager;
    private final TrackScheduler trackScheduler;

    private boolean isTrackSelectionInProgress = false;
    private AudioPlaylist searchPlaylist;

    public KalClient() {
        // Creates AudioPlayer instances and translates URLs to AudioTrack instances
        playerManager = new DefaultAudioPlayerManager();
        // This is an optimization strategy that Discord4J can utilize. It is not important to understand
        playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager();
        youtube.useOauth2(null, true);
        playerManager.registerSourceManager(youtube);
        // Allow playerManager to parse remote sources like YouTube links
        AudioSourceManagers.registerRemoteSources(playerManager);
        // Create an AudioPlayer so Discord4J can receive audio data
        AudioPlayer player = playerManager.createPlayer();
        audioProvider = new D4jAudioProvider(player);
        trackScheduler = new TrackScheduler(player);
        player.addListener(trackScheduler);

        initCommands();
    }

    public static void main(String[] args) {
        new KalClient().run(args);
    }

    private void run(String[] args) {
        GatewayDiscordClient client = DiscordClientBuilder.create(args[0]).build().login().block();

        assert client != null;
        client.getEventDispatcher().on(MessageCreateEvent.class)
                // subscribe is like block, in that it will *request* for action
                // to be done, but instead of blocking the thread, waiting for it
                // to finish, it will just execute the results asynchronously.
                .subscribe(event -> {
                    String content = event.getMessage().getContent(); // 3.1 Message.getContent() is a String
                    if (Character.isDigit(content.charAt(0)) && isTrackSelectionInProgress) {
                        isTrackSelectionInProgress = false;
                        join(event);
                        AudioTrack selected = searchPlaylist.getTracks().get(Integer.parseInt(content.charAt(0) + "") - 1);
                        trackScheduler.queue(selected, event);
                        printTrackInfo(selected, event);
                        join(event);
                    }
                    if (content.startsWith("!")) {
                        Optional<String> commandOptional = commands.keySet().stream().filter(it -> content.startsWith('!' + it)).findAny();
                        if (commandOptional.isPresent()) {
                            try {
                                commands.get(commandOptional.get()).execute(event);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            event.getMessage()
                                    .getChannel().block()
                                    .createMessage(Messages.UNRECOGNIZED_COMMAND).block();
                        }
                    }
                });

        client.onDisconnect().block();
    }

    private void initCommands() {
        commands.put(DiscordCommand.Ping.name, event -> event.getMessage()
                .getChannel().block()
                .createMessage("Я ЗАЕБАЛСЯ ЖДААААТЬ ОЛЬГА НЕСИ МОЙ КОКС Я ЗАЕБАЛСЯ ЖДАТЬ").block());

        commands.put(DiscordCommand.Join.name, this::join);

        commands.put(DiscordCommand.Play.name, event -> {
            join(event);
            play(event);
            Thread.sleep(1000);
            join(event);
        });

        commands.put(DiscordCommand.Pause.name, event -> {
            sendMessage("Pausing...", event);
            trackScheduler.stop(event);
        });

        commands.put(DiscordCommand.Skip.name, event -> {
            sendMessage("Skipping current track", event);
            trackScheduler.skip(event);
        });

        commands.put(DiscordCommand.Resume.name, event -> {
            sendMessage("Resuming...", event);
            trackScheduler.resume(event);
        });

        commands.put(DiscordCommand.Fullstop.name, event -> {
            sendMessage("Queue cleared, stopping", event);
            trackScheduler.stopAll(event);
        });

        commands.put(DiscordCommand.Help.name, event -> {
            StringBuilder message = new StringBuilder("List of available commands: \n");
            Arrays.stream(DiscordCommand.values()).forEach(it -> message.append("!").append(it.name).append("  -------  ").append(it.description).append("\n"));
            event.getMessage()
                    .getChannel().block()
                    .createMessage(message.toString()).block();
        });
    }

    private void join(MessageCreateEvent event) {
        final Member member = event.getMember().orElse(null);
        if (member != null) {
            final VoiceState voiceState = member.getVoiceState().block();
            if (voiceState != null) {
                final VoiceChannel channel = voiceState.getChannel().block();
                if (channel != null) {
                    // join returns a VoiceConnection which would be required if we were
                    // adding disconnection features, but for now we are just ignoring it.
                    channel.join(spec -> spec.setProvider(audioProvider)).block();
                }
            }
        }
    }

    private void play(MessageCreateEvent event) {
        String content = event.getMessage().getContent();
        List<String> command = new ArrayList<>(Arrays.asList(content.split(" ")));
        command.removeFirst();
        String url = String.join(" ", command);
        if (!isURL(url)) {
            url = "ytsearch:" + url;
        }
        playerManager.loadItem(url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                trackScheduler.queue(track, event);
                printTrackInfo(track, event);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    isTrackSelectionInProgress = true;
                    searchPlaylist = playlist;

                    printSearchResults(playlist, event);
                    return;
                }
                for (AudioTrack track : playlist.getTracks()) {
                    trackScheduler.queue(track, event);
                }
                printPlaylistInfo(playlist, event);
            }

            @Override
            public void noMatches() {
                // Notify the user that we've got nothing
            }

            @Override
            public void loadFailed(FriendlyException throwable) {
                // Notify the user that everything exploded
            }
        });
    }

    private boolean isURL(String s) {
        try {
            new URI(s);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private void printSearchResults(AudioPlaylist playlist, MessageCreateEvent event) {
        StringBuilder message = new StringBuilder("Search results, print the selected track number: \n");
        for (int i = 0; i < Math.min(playlist.getTracks().size(), MAX_SEARCH_RESULTS); i++) {
            AudioTrack track = playlist.getTracks().get(i);
            message.append(i).append(". ").append(track.getInfo().author).append(" — ").append(track.getInfo().title).append("\n");
        }
        sendMessage(message.toString(), event);
    }

    private void printTrackInfo(AudioTrack track, MessageCreateEvent event) {
        String message = "Queued track: " + track.getInfo().author + " — " + track.getInfo().title + " \n" + track.getInfo().uri;
        sendMessage(message, event);
    }

    private void printPlaylistInfo(AudioPlaylist playlist, MessageCreateEvent event) {
        String message = "Queued playlist: " + playlist.getName() + " of " + playlist.getTracks().size() + " tracks";
        sendMessage(message, event);
    }

    private void sendMessage(String message, MessageCreateEvent event) {
        event.getMessage()
                .getChannel().block()
                .createMessage(message.toString()).block();
    }
}