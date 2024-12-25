package org.example;

public enum DiscordCommand {
    Ping("ping", "ну андрюха берегись"),
    Join("join", "this command is useless but well bot will join your channel so you won't feel yourself alone"),
    Play("play", "plays the track or playlist"),
    Pause("pause", "Pauses the track"),
    Skip("skip", "skips current track, will tunr on the next on queue"),
    Resume("resume", "resumes the track"),
    Fullstop("stop", "stops the track and clears the queue so you well don't have anything in queue anymore and all the stuff you added is lost thats also is a good trolling method"),
    Help("help", "helps you to find out why you exist on this cursed planet");

    public final String name;
    public final String description;

    DiscordCommand(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
