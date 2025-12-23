# RLEnvironment

A Spigot plugin that runs a small reinforcement-learning environment inside a Minecraft world. It spawns a simple arena, drives an agent with a Q-learning policy, visualizes progress in-world, and logs transitions for offline analysis.

## Features
- Gold-collector environment with randomized terrain and goal placement
- Q-learning policy with epsilon decay and adaptive exploration
- In-world agent visualization (baby zombie) and arena outline
- Particle-based reward/epsilon graph (rolling or condensed modes)
- CSV transition logging for training/analysis
- Progression mode with simple level steps

## Requirements
- Java 17
- Spigot or Paper 1.20.x (built against Spigot API 1.20.2)
- Maven (for building)

## Build
```bash
mvn package
```

The plugin jar will be in `target/RLEnvironment-1.0.0-DEVELOPMENT.jar`.

## Install
1. Copy the jar to your server's `plugins/` folder.
2. Start or restart the server.

## Usage
Run commands in-game as a player. The arena is created near the player who starts it.

### Commands
- `/rlenv start` - Create the arena and start the environment
- `/rlenv stop` - Stop the environment and restore terrain
- `/rlenv status` - Show episode stats, success rate, and epsilon
- `/rlenv showarena` - Show an outline of the arena
- `/rlenv speed <stepsPerSecond>` - Control environment step rate
- `/rlenv graph` - Toggle the graph visualization on/off
- `/rlenv graph mode <rolling|condense>` - Switch graph display mode
- `/rlenv progression <start|next|stop>` - Run the simple progression levels

### Permissions
- `rlenv.use` (default: true)

## Data Logging
Transitions are appended to `plugins/RLEnvPlugin/transitions.csv`.

Each line logs:
- `obs` (semicolon-separated features)
- `action` (ordinal index)
- `reward`
- `next_obs`
- `done` (0/1)

## License
This project is licensed under the Creative Commons Attribution-NonCommercial 4.0 International License. See `LICENSE` for details.

## Development Notes
- Arena sizing and placement are in `src/main/java/me/evisual/rlenv/RLEnvPlugin.java`.
- Q-learning parameters live in `src/main/java/me/evisual/rlenv/control/QLearningPolicy.java`.
- The gold collector environment is implemented in `src/main/java/me/evisual/rlenv/env/goldcollector/GoldCollectorEnvironment.java`.
