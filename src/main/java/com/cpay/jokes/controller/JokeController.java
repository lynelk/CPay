package com.cpay.jokes.controller;

import com.cpay.jokes.service.JokeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jokes")
@CrossOrigin(origins = "*")
public class JokeController {

    @Autowired
    private JokeService jokeService;

    /**
     * Get a random joke
     * @return Random joke from external API
     */
    @GetMapping("/random")
    public ResponseEntity<?> getRandomJoke() {
        try {
            String joke = jokeService.getRandomJoke();
            return ResponseEntity.ok(new JokeResponse(joke, "success"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new JokeResponse(null, "Error: " + e.getMessage()));
        }
    }

    /**
     * Get a joke by type
     * @param type The type of joke (general, knock-knock, programming, etc.)
     * @return Joke of specified type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<?> getJokeByType(@PathVariable String type) {
        try {
            String joke = jokeService.getJokeByType(type);
            return ResponseEntity.ok(new JokeResponse(joke, "success"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new JokeResponse(null, "Error: " + e.getMessage()));
        }
    }

    /**
     * Inner class for API response
     */
    public static class JokeResponse {
        private String joke;
        private String status;

        public JokeResponse(String joke, String status) {
            this.joke = joke;
            this.status = status;
        }

        public String getJoke() {
            return joke;
        }

        public void setJoke(String joke) {
            this.joke = joke;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}