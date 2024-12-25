package org.example;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEvent;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener;
import com.sedmelluq.discord.lavaplayer.player.event.TrackEndEvent;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.core.event.domain.message.MessageCreateEvent;

import java.util.LinkedList;
import java.util.Queue;

public final class TrackScheduler implements AudioEventListener {

    private final AudioPlayer player;
    private final Queue<AudioTrack> queue = new LinkedList<>();
    private MessageCreateEvent lastEvent;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
    }

    @Override
    public synchronized void onEvent(AudioEvent event) {
        switch (event) {
            case TrackEndEvent tee -> {
                if (!queue.isEmpty()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (player.getPlayingTrack() == null) {
                        AudioTrack track = queue.poll();
                        player.playTrack(track);
                        sendTrackPlaying(track, lastEvent);
                    }
                }
            }
            default -> {}
        }
    }

    public synchronized void queue(AudioTrack track, MessageCreateEvent event) {
        lastEvent = event;
        queue.add(track);
        if (player.getPlayingTrack() == null) {
            player.playTrack(queue.poll());
        }
    }

    public synchronized void stop(MessageCreateEvent event) {
        lastEvent = event;
        player.setPaused(true);
    }

    public synchronized void resume(MessageCreateEvent event) {
        lastEvent = event;
        player.setPaused(false);
    }

    public synchronized void skip(MessageCreateEvent event) {
        lastEvent = event;
        player.stopTrack();
    }

    public synchronized void stopAll(MessageCreateEvent event) {
        lastEvent = event;
        while (!queue.isEmpty()) {
            queue.remove();
        }
        player.stopTrack();
    }

    private void sendTrackPlaying(AudioTrack track, MessageCreateEvent event) {
        String message = "Now playing: " + track.getInfo().author + " — " + track.getInfo().title + " \n" + track.getInfo().uri;
        event.getMessage()
                .getChannel().block()
                .createMessage(message.toString()).block();
    }
}