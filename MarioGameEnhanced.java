import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Iterator;

public class MarioGameEnhanced extends Application {
    private static final int GAME_WIDTH = 800;
    private static final int GAME_HEIGHT = 600;
    private static final double GRAVITY = 0.4;
    private static final int TILE_SIZE = 32;
    private static final double GAME_TIME_LIMIT = 60.0;
    private static final double LEVEL_END_X = 1500; // Level completion point

    private Canvas canvas;
    private GraphicsContext gc;
    private Set<KeyCode> pressedKeys = new HashSet<>();
    private Player player;
    private List<Platform> platforms;
    private List<Enemy> enemies;
    private List<Coin> coins;
    private List<PowerUp> powerUps;
    private List<QuestionBlock> questionBlocks;
    private List<Pipe> pipes;
    private List<Particle> particles;
    private List<Fireball> fireballs;
    private double cameraX = 0;
    private int score = 0;
    private int lives = 3;
    private int level = 1;
    private boolean gameOver = false;
    private boolean levelComplete = false;
    private long lastTime = 0;
    private boolean fireKeyPressed = false;
    private double gameTimer = GAME_TIME_LIMIT;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        canvas = new Canvas(GAME_WIDTH, GAME_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane();
        root.getChildren().add(canvas);

        Scene scene = new Scene(root, GAME_WIDTH, GAME_HEIGHT);

        scene.setOnKeyPressed(e -> pressedKeys.add(e.getCode()));
        scene.setOnKeyReleased(e -> pressedKeys.remove(e.getCode()));

        initializeGame();

        AnimationTimer gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastTime == 0) lastTime = now;
                double deltaTime = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;

                if (!gameOver && !levelComplete) {
                    update(deltaTime);
                }
                handleGameStateInput();
                render();
            }
        };
        gameLoop.start();

        primaryStage.setTitle("Super Mario Bros Style Game");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        canvas.requestFocus();
    }

    private void initializeGame() {
        player = new Player(100, 400);
        platforms = new ArrayList<>();
        enemies = new ArrayList<>();
        coins = new ArrayList<>();
        powerUps = new ArrayList<>();
        questionBlocks = new ArrayList<>();
        pipes = new ArrayList<>();
        particles = new ArrayList<>();
        fireballs = new ArrayList<>();
        gameTimer = GAME_TIME_LIMIT;
        levelComplete = false;

        createLevel();
    }

    private void createLevel() {
        // Ground platforms
        for (int i = 0; i < 60; i++) {
            platforms.add(new Platform(i * TILE_SIZE, GAME_HEIGHT - TILE_SIZE, TILE_SIZE, TILE_SIZE, PlatformType.GROUND));
        }

        // Simple brick platform for testing
        for (int i = 0; i < 4; i++) {
            platforms.add(new Platform(300 + i * TILE_SIZE, GAME_HEIGHT - TILE_SIZE * 3, TILE_SIZE, 16, PlatformType.BRICK));
        }

        // One floating platform
        platforms.add(new Platform(600, GAME_HEIGHT - TILE_SIZE * 4, TILE_SIZE * 3, 20, PlatformType.GROUND));

        // Single pipe at the end
        pipes.add(new Pipe(LEVEL_END_X - 100, GAME_HEIGHT - TILE_SIZE * 3, TILE_SIZE * 2, TILE_SIZE * 2));

        // Only 2 question blocks - positioned for EASY hitting
        // First block: Right above the brick platform, very close
        questionBlocks.add(new QuestionBlock(350, GAME_HEIGHT - TILE_SIZE * 3 - TILE_SIZE - 2, PowerUpType.MUSHROOM));

        // Second block: At ground level, easy jump height
        questionBlocks.add(new QuestionBlock(500, GAME_HEIGHT - TILE_SIZE - TILE_SIZE - 10, PowerUpType.FIRE_FLOWER));

        // Simple enemies
        enemies.add(new Goomba(400, GAME_HEIGHT - TILE_SIZE - 24));
        enemies.add(new Goomba(700, GAME_HEIGHT - TILE_SIZE - 24));

        // Some coins for collection
        coins.add(new Coin(250, GAME_HEIGHT - TILE_SIZE - 30));
        coins.add(new Coin(280, GAME_HEIGHT - TILE_SIZE - 30));
        coins.add(new Coin(1300, GAME_HEIGHT - TILE_SIZE - 30));
        coins.add(new Coin(1330, GAME_HEIGHT - TILE_SIZE - 30));
    }

    private void update(double deltaTime) {
        handleInput();

        // Update game timer
        gameTimer -= deltaTime;
        if (gameTimer <= 0) {
            gameOver = true;
            gameTimer = 0;
        }

        player.update(deltaTime);

        // Check level completion
        if (player.x >= LEVEL_END_X) {
            levelComplete = true;
        }

        // Update enemies
        for (Enemy enemy : enemies) {
            enemy.update(deltaTime);
        }

        // Update fireballs
        Iterator<Fireball> fireballIter = fireballs.iterator();
        while (fireballIter.hasNext()) {
            Fireball fireball = fireballIter.next();
            fireball.update(deltaTime);
            if (fireball.isDead()) {
                fireballIter.remove();
            }
        }

        // Update particles
        Iterator<Particle> particleIter = particles.iterator();
        while (particleIter.hasNext()) {
            Particle particle = particleIter.next();
            particle.update(deltaTime);
            if (particle.isDead()) {
                particleIter.remove();
            }
        }

        checkCollisions();
        updateCamera();

        // Check game over conditions
        if (player.y > GAME_HEIGHT + 100) {
            lives--;
            if (lives <= 0) {
                gameOver = true;
            } else {
                player.reset();
            }
        }
    }

    private void handleInput() {
        // Movement - slower
        boolean leftPressed = pressedKeys.contains(KeyCode.LEFT) || pressedKeys.contains(KeyCode.A);
        boolean rightPressed = pressedKeys.contains(KeyCode.RIGHT) || pressedKeys.contains(KeyCode.D);

        if (leftPressed && !rightPressed) {
            player.moveLeft();
        } else if (rightPressed && !leftPressed) {
            player.moveRight();
        } else {
            player.applyMovementFriction();
        }

        // Jump
        boolean jumpPressed = pressedKeys.contains(KeyCode.SPACE) ||
                pressedKeys.contains(KeyCode.UP) || pressedKeys.contains(KeyCode.W);

        if (jumpPressed) {
            player.jump();
        }

        // Variable jump height
        if (!jumpPressed && player.velY < -2) {
            player.velY *= 0.5;
        }

        // Run button
        player.setRunning(pressedKeys.contains(KeyCode.SHIFT));

        // Fire button
        boolean firePressed = pressedKeys.contains(KeyCode.X) || pressedKeys.contains(KeyCode.CONTROL);

        if (firePressed && !fireKeyPressed && player.powerState == PowerState.FIRE) {
            shootFireball();
        }
        fireKeyPressed = firePressed;
    }

    private void handleGameStateInput() {
        // Handle restart and play again
        if ((gameOver || levelComplete) && pressedKeys.contains(KeyCode.R)) {
            restartGame();
        }
    }

    private void shootFireball() {
        if (fireballs.size() < 2) {
            double fireballX = player.facingRight ? player.x + player.width : player.x - 8;
            double fireballY = player.y + player.height / 2;
            fireballs.add(new Fireball(fireballX, fireballY, player.facingRight));
        }
    }

    private void checkCollisions() {
        // Platform collisions
        for (Platform platform : platforms) {
            if (player.intersects(platform)) {
                handlePlatformCollision(player, platform);
            }
        }

        // Enemy collisions
        Iterator<Enemy> enemyIter = enemies.iterator();
        while (enemyIter.hasNext()) {
            Enemy enemy = enemyIter.next();
            if (player.intersects(enemy) && !player.isInvincible()) {
                if (player.velY > 0 && player.y < enemy.y - 5) {
                    // Stomp enemy
                    enemy.stomp();
                    if (enemy.isDead()) {
                        enemyIter.remove();
                        score += 100;
                        addScoreParticle(enemy.x, enemy.y, "100");
                    }
                    player.velY = -8;
                } else {
                    // Player hit
                    player.takeDamage();
                }
            }
        }

        // Fireball-enemy collisions
        Iterator<Fireball> fireballIter = fireballs.iterator();
        while (fireballIter.hasNext()) {
            Fireball fireball = fireballIter.next();
            Iterator<Enemy> enemyIter2 = enemies.iterator();
            while (enemyIter2.hasNext()) {
                Enemy enemy = enemyIter2.next();
                if (fireball.intersects(enemy)) {
                    enemy.stomp();
                    if (enemy.isDead()) {
                        enemyIter2.remove();
                        score += 200;
                        addScoreParticle(enemy.x, enemy.y, "200");
                    }
                    fireball.setDead();
                    break;
                }
            }
        }

        // Question block collisions - MUCH more lenient detection
        for (QuestionBlock block : questionBlocks) {
            if (!block.isUsed() && player.intersects(block)) {
                // Much simpler collision detection - if player touches block and is moving up
                if (player.velY <= 0 && player.y < block.y + block.height) {
                    block.hit();
                    PowerUp powerUp = block.spawnPowerUp();
                    if (powerUp != null) {
                        powerUps.add(powerUp);
                    }

                    // Add coin directly to score if it's a coin block
                    if (block.powerUpType == PowerUpType.COIN) {
                        score += 200;
                        addScoreParticle(block.x + block.width/2, block.y, "200");
                    }

                    // Bounce player down slightly
                    player.velY = 2;

                    // Add hit effect
                    addHitParticle(block.x + block.width/2, block.y);
                }
            }
        }

        // Power-up collisions
        Iterator<PowerUp> powerUpIter = powerUps.iterator();
        while (powerUpIter.hasNext()) {
            PowerUp powerUp = powerUpIter.next();
            powerUp.update(0.016);

            if (player.intersects(powerUp)) {
                player.collectPowerUp(powerUp.type);
                powerUpIter.remove();
                score += 1000;
                addScoreParticle(powerUp.x, powerUp.y, "1000");
            }
        }

        // Coin collisions
        Iterator<Coin> coinIter = coins.iterator();
        while (coinIter.hasNext()) {
            Coin coin = coinIter.next();
            if (player.intersects(coin)) {
                coinIter.remove();
                score += 200;
                addScoreParticle(coin.x, coin.y, "200");
            }
        }
    }

    private void handlePlatformCollision(Player player, Platform platform) {
        double playerBottom = player.y + player.height;
        double playerRight = player.x + player.width;
        double platformBottom = platform.y + platform.height;
        double platformRight = platform.x + platform.width;

        double overlapLeft = playerRight - platform.x;
        double overlapRight = platformRight - player.x;
        double overlapTop = playerBottom - platform.y;
        double overlapBottom = platformBottom - player.y;

        double minOverlap = Math.min(Math.min(overlapLeft, overlapRight), Math.min(overlapTop, overlapBottom));

        if (minOverlap == overlapTop && player.velY >= 0) {
            player.y = platform.y - player.height;
            player.velY = 0;
            player.onGround = true;
        } else if (minOverlap == overlapBottom && player.velY < 0) {
            player.y = platform.y + platform.height;
            player.velY = 1;
        } else if (minOverlap == overlapLeft && player.velX > 0) {
            player.x = platform.x - player.width;
            player.velX = 0;
        } else if (minOverlap == overlapRight && player.velX < 0) {
            player.x = platform.x + platform.width;
            player.velX = 0;
        }
    }

    private void updateCamera() {
        double targetCameraX = player.x - GAME_WIDTH / 3;
        cameraX += (targetCameraX - cameraX) * 0.1;
        if (cameraX < 0) cameraX = 0;
    }

    private void addScoreParticle(double x, double y, String text) {
        particles.add(new ScoreParticle(x, y, text));
    }

    private void addHitParticle(double x, double y) {
        particles.add(new HitParticle(x, y));
    }

    private void restartGame() {
        gameOver = false;
        levelComplete = false;
        lives = 3;
        score = 0;
        cameraX = 0;
        gameTimer = GAME_TIME_LIMIT;
        player.reset();
        player.powerState = PowerState.SMALL;
        enemies.clear();
        coins.clear();
        powerUps.clear();
        particles.clear();
        fireballs.clear();

        // Reset question blocks
        for (QuestionBlock block : questionBlocks) {
            block.reset();
        }

        createLevel();
    }

    private void render() {
        // Clear screen with sky blue
        gc.setFill(Color.LIGHTBLUE);
        gc.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

        gc.save();
        gc.translate(-cameraX, 0);

        // Draw platforms
        for (Platform platform : platforms) {
            platform.draw(gc);
        }

        // Draw pipes
        for (Pipe pipe : pipes) {
            pipe.draw(gc);
        }

        // Draw question blocks
        for (QuestionBlock block : questionBlocks) {
            block.draw(gc);
        }

        // Draw power-ups
        for (PowerUp powerUp : powerUps) {
            powerUp.draw(gc);
        }

        // Draw enemies
        for (Enemy enemy : enemies) {
            enemy.draw(gc);
        }

        // Draw coins
        for (Coin coin : coins) {
            coin.draw(gc);
        }

        // Draw fireballs
        for (Fireball fireball : fireballs) {
            fireball.draw(gc);
        }

        // Draw particles
        for (Particle particle : particles) {
            particle.draw(gc);
        }

        // Draw player
        player.draw(gc);

        gc.restore();

        // Draw UI
        drawUI();
    }

    private void drawUI() {
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        gc.setFill(Color.WHITE);
        gc.fillText("MARIO", 20, 30);
        gc.fillText(String.format("%06d", score), 20, 50);

        gc.fillText("WORLD", 200, 30);
        gc.fillText("1-" + level, 200, 50);

        gc.fillText("TIME", 300, 30);
        gc.fillText(String.format("%03d", (int)Math.ceil(gameTimer)), 300, 50);

        gc.fillText("LIVES: " + lives, 400, 30);

        // Display current power state
        String powerText = "POWER: ";
        switch(player.powerState) {
            case SMALL: powerText += "SMALL"; break;
            case BIG: powerText += "SUPER"; break;
            case FIRE: powerText += "FIRE"; break;
        }
        gc.fillText(powerText, 500, 30);

        // Instructions
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 12));
        gc.fillText("Jump into question blocks from below to hit them!", 20, GAME_HEIGHT - 20);

        if (levelComplete) {
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 48));
            gc.setFill(Color.GREEN);
            gc.fillText("CONGRATULATIONS!", GAME_WIDTH/2 - 180, GAME_HEIGHT/2 - 40);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 24));
            gc.setFill(Color.WHITE);
            gc.fillText("Level Complete! Press R to restart", GAME_WIDTH/2 - 120, GAME_HEIGHT/2);
        } else if (gameOver) {
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 48));
            gc.setFill(Color.RED);
            gc.fillText("GAME OVER", GAME_WIDTH/2 - 120, GAME_HEIGHT/2);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 20));
            gc.setFill(Color.WHITE);
            if (gameTimer <= 0) {
                gc.fillText("TIME'S UP!", GAME_WIDTH/2 - 45, GAME_HEIGHT/2 - 60);
            }
            gc.fillText("Press R to restart", GAME_WIDTH/2 - 70, GAME_HEIGHT/2 + 30);
        }
    }

    // Enums
    enum PowerState { SMALL, BIG, FIRE }
    enum PowerUpType { MUSHROOM, FIRE_FLOWER, STAR, COIN }
    enum PlatformType { GROUND, BRICK, PIPE }

    // Fireball class
    class Fireball {
        double x, y, velX, velY;
        double width = 8, height = 8;
        boolean dead = false;
        double lifeTime = 5.0;

        public Fireball(double x, double y, boolean facingRight) {
            this.x = x;
            this.y = y;
            this.velX = facingRight ? 6 : -6;
            this.velY = -2;
        }

        public void update(double deltaTime) {
            x += velX;
            y += velY;
            velY += 0.2;

            for (Platform platform : platforms) {
                if (intersects(platform)) {
                    if (velY > 0 && y < platform.y) {
                        y = platform.y - height;
                        velY = -4;
                    }
                }
            }

            lifeTime -= deltaTime;
            if (lifeTime <= 0 || x < -100 || x > 2000) {
                dead = true;
            }
        }

        public boolean intersects(Platform platform) {
            return x < platform.x + platform.width &&
                    x + width > platform.x &&
                    y < platform.y + platform.height &&
                    y + height > platform.y;
        }

        public boolean intersects(Enemy enemy) {
            return x < enemy.x + enemy.width &&
                    x + width > enemy.x &&
                    y < enemy.y + enemy.height &&
                    y + height > enemy.y;
        }

        public boolean isDead() { return dead; }
        public void setDead() { dead = true; }

        public void draw(GraphicsContext gc) {
            gc.setFill(Color.ORANGE);
            gc.fillOval(x, y, width, height);
            gc.setFill(Color.RED);
            gc.fillOval(x + 1, y + 1, width - 2, height - 2);
        }
    }

    // Player class with slower movement
    class Player {
        double x, y, velX, velY;
        double width = 24, height = 32;
        boolean onGround = false;
        boolean running = false;
        PowerState powerState = PowerState.SMALL;
        double startX, startY;
        private double invincibilityTimer = 0;
        boolean facingRight = true;
        private boolean jumpRequested = false;
        private double jumpBufferTime = 0;
        private static final double JUMP_BUFFER_DURATION = 0.1;

        public Player(double x, double y) {
            this.x = x;
            this.y = y;
            this.startX = x;
            this.startY = y;
        }

        public void update(double deltaTime) {
            if (invincibilityTimer > 0) {
                invincibilityTimer -= deltaTime;
            }

            if (jumpBufferTime > 0) {
                jumpBufferTime -= deltaTime;
            }

            if (!onGround) {
                velY += GRAVITY;
                if (velY > 12) velY = 12;
            }

            x += velX;
            y += velY;

            onGround = false;

            if (x < 0) x = 0;

            if (powerState == PowerState.SMALL) {
                height = 32;
                width = 24;
            } else {
                height = 48;
                width = 32;
            }
        }

        public void moveLeft() {
            facingRight = false;
            double acceleration = running ? 0.15 : 0.1;
            double maxSpeed = running ? 2.5 : 1.8;

            if (velX > 0) {
                velX -= acceleration * 2;
            } else {
                velX -= acceleration;
            }

            if (velX < -maxSpeed) velX = -maxSpeed;
        }

        public void moveRight() {
            facingRight = true;
            double acceleration = running ? 0.15 : 0.1;
            double maxSpeed = running ? 2.5 : 1.8;

            if (velX < 0) {
                velX += acceleration * 2;
            } else {
                velX += acceleration;
            }

            if (velX > maxSpeed) velX = maxSpeed;
        }

        public void applyMovementFriction() {
            if (onGround) {
                velX *= 0.85;
            } else {
                velX *= 0.95;
            }

            if (Math.abs(velX) < 0.1) velX = 0;
        }

        public void jump() {
            jumpRequested = true;
            jumpBufferTime = JUMP_BUFFER_DURATION;

            if (onGround) {
                performJump();
            }
        }

        private void performJump() {
            if (jumpRequested && (onGround || jumpBufferTime > 0)) {
                velY = running ? -12 : -9.0;
                onGround = false;
                jumpRequested = false;
                jumpBufferTime = 0;
            }
        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        public void takeDamage() {
            if (invincibilityTimer > 0) return;

            if (powerState == PowerState.FIRE) {
                powerState = PowerState.BIG;
            } else if (powerState == PowerState.BIG) {
                powerState = PowerState.SMALL;
            } else {
                lives--;
                if (lives > 0) {
                    reset();
                } else {
                    gameOver = true;
                }
            }
            invincibilityTimer = 2.0;
        }

        public void collectPowerUp(PowerUpType type) {
            switch (type) {
                case MUSHROOM:
                    if (powerState == PowerState.SMALL) {
                        powerState = PowerState.BIG;
                    }
                    break;
                case FIRE_FLOWER:
                    powerState = PowerState.FIRE;
                    break;
                case STAR:
                    invincibilityTimer = 10.0;
                    break;
            }
        }

        public boolean isInvincible() {
            return invincibilityTimer > 0;
        }

        public void reset() {
            x = startX;
            y = startY;
            velX = 0;
            velY = 0;
            invincibilityTimer = 2.0;
        }

        public boolean intersects(GameObject obj) {
            return x < obj.x + obj.width &&
                    x + width > obj.x &&
                    y < obj.y + obj.height &&
                    y + height > obj.y;
        }

        public void draw(GraphicsContext gc) {
            if (invincibilityTimer > 0 && ((int)(invincibilityTimer * 10) % 2 == 0)) {
                return;
            }

            Color bodyColor = powerState == PowerState.FIRE ? Color.WHITE : Color.RED;
            Color overallColor = Color.BLUE;

            gc.setFill(bodyColor);
            gc.fillRect(x + 2, y + height/2, width - 4, height/2);

            gc.setFill(overallColor);
            gc.fillRect(x + 4, y + height/2 + 2, width - 8, height/2 - 4);

            gc.setFill(Color.PEACHPUFF);
            gc.fillOval(x, y, width, height/2 + 4);

            gc.setFill(Color.RED);
            gc.fillOval(x + 2, y, width - 4, height/4);

            gc.setFill(Color.BLACK);
            double eyeSize = powerState == PowerState.SMALL ? 3 : 4;
            gc.fillOval(x + width/4, y + height/6, eyeSize, eyeSize);
            gc.fillOval(x + 3*width/4 - eyeSize, y + height/6, eyeSize, eyeSize);

            gc.setFill(Color.BROWN);
            gc.fillRect(x + width/3, y + height/3, width/3, 3);
        }
    }

    // GameObject base class
    abstract class GameObject {
        double x, y, width, height;

        public GameObject(double x, double y, double width, double height)
        {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean intersects(GameObject other) {
            return x < other.x + other.width &&
                    x + width > other.x &&
                    y < other.y + other.height &&
                    y + height > other.y;
        }

        public abstract void draw(GraphicsContext gc);
    }

    // Platform class
    class Platform extends GameObject {
        PlatformType type;

        public Platform(double x, double y, double width, double height, PlatformType type) {
            super(x, y, width, height);
            this.type = type;
        }

        @Override
        public void draw(GraphicsContext gc) {
            switch (type) {
                case GROUND:
                    gc.setFill(Color.GREEN);
                    gc.fillRect(x, y, width, height);
                    gc.setFill(Color.DARKGREEN);
                    gc.strokeRect(x, y, width, height);
                    break;
                case BRICK:
                    gc.setFill(Color.ORANGE);
                    gc.fillRect(x, y, width, height);
                    gc.setFill(Color.DARKORANGE);
                    gc.strokeRect(x, y, width, height);
                    // Draw brick pattern
                    for (int i = 0; i < width; i += 16) {
                        gc.strokeLine(x + i, y, x + i, y + height);
                    }
                    break;
                case PIPE:
                    gc.setFill(Color.LIGHTGREEN);
                    gc.fillRect(x, y, width, height);
                    gc.setFill(Color.DARKGREEN);
                    gc.strokeRect(x, y, width, height);
                    break;
            }
        }
    }

    // Enemy base class
    abstract class Enemy extends GameObject {
        double velX = -1;
        boolean dead = false;
        double startX;

        public Enemy(double x, double y, double width, double height) {
            super(x, y, width, height);
            this.startX = x;
        }

        public void update(double deltaTime) {
            if (dead) return;

            x += velX;

            // Check platform collisions
            boolean onPlatform = false;
            for (Platform platform : platforms) {
                if (intersects(platform)) {
                    if (velX > 0) {
                        x = platform.x - width;
                        velX = -velX;
                    } else {
                        x = platform.x + platform.width;
                        velX = -velX;
                    }
                }

                // Check if enemy is on a platform
                if (y + height <= platform.y + 5 && y + height >= platform.y - 5 &&
                        x + width/2 >= platform.x && x + width/2 <= platform.x + platform.width)
                {
                    onPlatform = true;
                    break;
                }
            }

            // Turn around at edges if not on platform
            if (!onPlatform) {
                velX = -velX;
            }

            // Boundary checks
            if (x < startX - 200) {
                velX = Math.abs(velX);
            } else if (x > startX + 200) {
                velX = -Math.abs(velX);
            }
        }

        public void stomp() {
            dead = true;
        }

        public boolean isDead() {
            return dead;
        }
    }

    // Goomba enemy
    class Goomba extends Enemy {
        public Goomba(double x, double y) {
            super(x, y, 24, 24);
        }

        @Override
        public void draw(GraphicsContext gc) {
            if (dead) return;

            gc.setFill(Color.BROWN);
            gc.fillOval(x, y, width, height);
            gc.setFill(Color.BLACK);
            gc.fillOval(x + 2, y + 2, width - 4, height - 4);

            // Eyes
            gc.setFill(Color.BLACK);
            gc.fillOval(x + 6, y + 6, 4, 4);
            gc.fillOval(x + 14, y + 6, 4, 4);

            // Frown
            gc.strokeLine(x + 8, y + 16, x + 16, y + 16);
        }
    }

    // Coin class
    class Coin extends GameObject {
        private double animationTimer = 0;

        public Coin(double x, double y) {
            super(x, y, 16, 16);
        }

        public void update(double deltaTime) {
            animationTimer += deltaTime * 8;
        }

        @Override
        public void draw(GraphicsContext gc) {
            double scale = Math.abs(Math.sin(animationTimer)) * 0.3 + 0.7;

            gc.setFill(Color.GOLD);
            gc.fillOval(x + width * (1 - scale) / 2, y, width * scale, height);

            gc.setFill(Color.ORANGE);
            gc.fillOval(x + width * (1 - scale) / 2 + 2, y + 2, (width - 4) * scale, height - 4);
        }
    }

    // PowerUp class
    class PowerUp extends GameObject {
        PowerUpType type;
        double velX = 2;
        double velY = 0;

        public PowerUp(double x, double y, PowerUpType type) {
            super(x, y, 24, 24);
            this.type = type;
            if (type == PowerUpType.MUSHROOM) {
                this.velY = -4; // Initial upward velocity when spawned
            }
        }

        public void update(double deltaTime) {
            if (type == PowerUpType.MUSHROOM) {
                x += velX;
                y += velY;
                velY += GRAVITY * 0.5; // Lighter gravity for power-ups

                // Platform collisions for mushroom
                for (Platform platform : platforms) {
                    if (intersects(platform)) {
                        if (velY > 0 && y < platform.y) {
                            y = platform.y - height;
                            velY = 0;
                        } else if (velX > 0 && x < platform.x) {
                            x = platform.x - width;
                            velX = -velX;
                        } else if (velX < 0 && x > platform.x) {
                            x = platform.x + platform.width;
                            velX = -velX;
                        }
                    }
                }
            }
        }

        @Override
        public void draw(GraphicsContext gc) {
            switch (type) {
                case MUSHROOM:
                    // Mushroom cap
                    gc.setFill(Color.RED);
                    gc.fillOval(x, y, width, height * 0.6);
                    // White spots
                    gc.setFill(Color.WHITE);
                    gc.fillOval(x + 4, y + 4, 4, 4);
                    gc.fillOval(x + 14, y + 8, 4, 4);
                    // Stem
                    gc.setFill(Color.BEIGE);
                    gc.fillRect(x + width/3, y + height * 0.4, width/3, height * 0.6);
                    break;
                case FIRE_FLOWER:
                    // Stem
                    gc.setFill(Color.GREEN);
                    gc.fillRect(x + width/2 - 2, y + height/2, 4, height/2);
                    // Petals
                    gc.setFill(Color.RED);
                    gc.fillOval(x + 4, y + 4, 8, 8);
                    gc.fillOval(x + 12, y + 4, 8, 8);
                    gc.fillOval(x + 8, y, 8, 8);
                    gc.fillOval(x + 8, y + 8, 8, 8);
                    // Center
                    gc.setFill(Color.YELLOW);
                    gc.fillOval(x + 8, y + 4, 8, 8);
                    break;
            }
        }
    }

    // QuestionBlock class
    class QuestionBlock extends GameObject {
        PowerUpType powerUpType;
        boolean used = false;
        private double animationTimer = 0;

        public QuestionBlock(double x, double y, PowerUpType powerUpType) {
            super(x, y, TILE_SIZE, TILE_SIZE);
            this.powerUpType = powerUpType;
        }

        public void update(double deltaTime) {
            animationTimer += deltaTime * 4;
        }

        public void hit() {
            if (!used) {
                used = true;
            }
        }

        public PowerUp spawnPowerUp() {
            if (powerUpType == PowerUpType.COIN) {
                return null; // Coins are handled directly in collision
            }
            return new PowerUp(x, y - 24, powerUpType);
        }

        public boolean isUsed() {
            return used;
        }

        public void reset() {
            used = false;
        }

        @Override
        public void draw(GraphicsContext gc) {
            if (used) {
                // Empty block
                gc.setFill(Color.DARKGRAY);
                gc.fillRect(x, y, width, height);
                gc.setFill(Color.GRAY);
                gc.strokeRect(x, y, width, height);
            } else {
                // Question block with animation
                double brightness = Math.sin(animationTimer) * 0.2 + 0.8;
                Color blockColor = Color.color(1.0 * brightness, 0.8 * brightness, 0.0);

                gc.setFill(blockColor);
                gc.fillRect(x, y, width, height);
                gc.setFill(Color.DARKORANGE);
                gc.strokeRect(x, y, width, height);

                // Question mark
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Arial", FontWeight.BOLD, 20));
                gc.fillText("?", x + width/2 - 6, y + height/2 + 7);
            }
        }
    }

    // Pipe class
    class Pipe extends GameObject {
        public Pipe(double x, double y, double width, double height) {
            super(x, y, width, height);
        }

        @Override
        public void draw(GraphicsContext gc) {
            // Pipe body
            gc.setFill(Color.LIGHTGREEN);
            gc.fillRect(x, y, width, height);

            // Pipe rim (top part)
            gc.setFill(Color.GREEN);
            gc.fillRect(x - 4, y, width + 8, 8);

            // Pipe outline
            gc.setFill(Color.DARKGREEN);
            gc.strokeRect(x, y, width, height);
            gc.strokeRect(x - 4, y, width + 8, 8);

            // Pipe details
            gc.strokeLine(x + width/2, y + 8, x + width/2, y + height);
        }
    }

    // Particle base class
    abstract class Particle {
        double x, y, velY;
        double lifeTime;
        double maxLifeTime;

        public Particle(double x, double y, double lifeTime) {
            this.x = x;
            this.y = y;
            this.lifeTime = lifeTime;
            this.maxLifeTime = lifeTime;
            this.velY = -2;
        }

        public void update(double deltaTime) {
            y += velY;
            lifeTime -= deltaTime;
        }

        public boolean isDead() {
            return lifeTime <= 0;
        }

        public abstract void draw(GraphicsContext gc);
    }

    // ScoreParticle class
    class ScoreParticle extends Particle {
        String text;

        public ScoreParticle(double x, double y, String text) {
            super(x, y, 1.5);
            this.text = text;
        }

        @Override
        public void draw(GraphicsContext gc) {
            double alpha = lifeTime / maxLifeTime;
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            gc.setFill(Color.color(1, 1, 1, alpha));
            gc.fillText(text, x, y);
        }
    }

    // HitParticle class
    class HitParticle extends Particle {
        public HitParticle(double x, double y) {
            super(x, y, 0.5);
            this.velY = -4;
        }

        @Override
        public void draw(GraphicsContext gc) {
            double alpha = lifeTime / maxLifeTime;
            gc.setFill(Color.color(1, 1, 0, alpha));
            gc.fillOval(x - 2, y - 2, 4, 4);
            gc.fillOval(x - 4, y, 4, 4);
            gc.fillOval(x + 2, y, 4, 4);
        }
    }
}