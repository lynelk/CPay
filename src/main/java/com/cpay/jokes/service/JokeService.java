package com.cpay.jokes.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

@Service
public class JokeService {

    private final RestTemplate restTemplate;

    public JokeService() {
        this.restTemplate = new RestTemplate();
    }

    public String fetchRandomJoke() {
        try {
            String response = restTemplate.getForObject("https://official-joke-api.appspot.com/jokes/random", String.class);
            return parseJoke(response);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // Handle the error
            return "Error fetching the joke: " + e.getMessage();
        }
    }

    public String fetchJokeByType(String type) {
        try {
            String response = restTemplate.getForObject(String.format("https://official-joke-api.appspot.com/jokes/%s/random", type), String.class);
            return parseJoke(response);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // Handle the error
            return "Error fetching the joke: " + e.getMessage();
        }
    }

    private String parseJoke(String jsonResponse) {
        // Assuming the response is a JSON string with the format:
        // { "setup": "...", "punchline": "..." }
        // Replace with your JSON parsing logic here (e.g., using Gson or Jackson)
        if (jsonResponse != null) {
            // Example parsing logic, replace it with a JSON library call
            return jsonResponse;
        }
        return "No joke found.";
    }
}