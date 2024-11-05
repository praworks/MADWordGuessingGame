package com.example.madwordguessinggame;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "UserPrefs";
    private static final String USER_NAME_KEY = "UserName";
    private String ninjas_API_KEY;
    private String dreamlo_PUBLIC_KEY;
    private String dreamlo_PRIVATE_KEY;

    private TextView welcomeTextView;
    private TextView wordDisplayTextView;
    private EditText userInputEditText;
    private Button submitButton;
    private Button requestHintButton;
    private Button requestLengthButton;
    private Button requestTipButton;
    private TextView gameStatusTextView;
    private TextView finalScoreTextView;
    private TextView timerDisplayTextView;
    private TextView apiStatusTextView;
    private TextView devWordDisplayTextView;
    private TextView leaderboardTextView;

    private String targetWord;
    private int score;
    private int maxAttempts = 10;
    private int attemptsLeft;
    private StringBuilder emptyWordDisplay;
    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadApiKeys();

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String userName = sharedPreferences.getString(USER_NAME_KEY, null);

        if (userName == null) {
            setContentView(R.layout.activity_welcome);
            EditText userNameInput = findViewById(R.id.user_name_input);
            Button startGameButton = findViewById(R.id.submit_name_button);

            startGameButton.setOnClickListener(v -> {
                String name = userNameInput.getText().toString().trim();
                if (!name.isEmpty()) {
                    sharedPreferences.edit().putString(USER_NAME_KEY, name).apply();
                    startGame(name);
                } else {
                    Toast.makeText(MainActivity.this, "Please enter a name", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            startGame(userName);
        }
    }

    private void loadApiKeys() {
        Properties properties = new Properties();
        try {
            InputStream inputStream = getAssets().open("secure-keys.properties");
            properties.load(inputStream);
            ninjas_API_KEY = properties.getProperty("NINJAS_API_KEY");
            dreamlo_PUBLIC_KEY = properties.getProperty("DREAMLO_PUBLIC_KEY");
            dreamlo_PRIVATE_KEY = properties.getProperty("DREAMLO_PRIVATE_KEY");
        } catch (IOException e) {
            Log.e(TAG, "Error loading API keys", e);
        }
    }

    private void startGame(String userName) {
        fetchLeaderboard();
        setContentView(R.layout.activity_main);
        initializeViews(userName);
        fetchRandomWord();
    }

    private void initializeViews(String userName) {
        welcomeTextView = findViewById(R.id.welcome_message);
        wordDisplayTextView = findViewById(R.id.word_display);
        userInputEditText = findViewById(R.id.user_input);
        submitButton = findViewById(R.id.submit_button);
        requestHintButton = findViewById(R.id.request_hint_button);
        requestLengthButton = findViewById(R.id.request_length_button);
        requestTipButton = findViewById(R.id.request_tip_button);
        gameStatusTextView = findViewById(R.id.game_status);
        finalScoreTextView = findViewById(R.id.final_score);
        timerDisplayTextView = findViewById(R.id.timer_display);
        apiStatusTextView = findViewById(R.id.api_status);
        devWordDisplayTextView = findViewById(R.id.dev_word_display);
        leaderboardTextView = findViewById(R.id.leaderboard);

        submitButton.setEnabled(false);
        welcomeTextView.setText("Welcome, " + userName + "!");
        apiStatusTextView.setText("Fetching word...");
        setButtonListeners();
    }

    private void setButtonListeners() {
        submitButton.setOnClickListener(v -> checkGuess());
        requestHintButton.setOnClickListener(v -> requestLetterOccurrence());
        requestLengthButton.setOnClickListener(v -> requestWordLength());
        requestTipButton.setOnClickListener(v -> {
            if (attemptsLeft <= maxAttempts - 5) {
                requestWordTip();
            } else {
                showToast("You can request a tip after 5 attempts.", Toast.LENGTH_LONG);
            }
        });
    }

    private void fetchRandomWord() {
        if (!isNetworkAvailable()) {
            showToast("No internet connection available.", Toast.LENGTH_LONG);
            apiStatusTextView.setText("Network error. Please try again.");
            return;
        }

        String url = "https://api.api-ninjas.com/v1/randomword";
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        targetWord = response.getString("word").replace("\"", "").replace("[", "").replace("]", "").trim();
                        if (targetWord != null && !targetWord.isEmpty()) {
                            setupGame();
                            submitButton.setEnabled(true);
                            apiStatusTextView.setText("Ready to play!");
                            devWordDisplayTextView.setText("Secret Word: " + targetWord);
                        } else {
                            showToast("Fetched word is empty or null.", Toast.LENGTH_LONG);
                            apiStatusTextView.setText("Error fetching word.");
                        }
                    } catch (Exception e) {
                        showToast("Error fetching the word: " + e.getMessage(), Toast.LENGTH_LONG);
                        apiStatusTextView.setText("Error processing word.");
                    }
                },
                error -> {
                    showToast("Network error, please try again. Error: " + error.toString(), Toast.LENGTH_LONG);
                    apiStatusTextView.setText("Network error. Please try again.");
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("X-API-Key", ninjas_API_KEY);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(jsonObjectRequest);
    }

    private void fetchLeaderboard() {
        if (!isNetworkAvailable()) {
            leaderboardTextView.setText("Error: No internet connection.");
            showToast("Error: No internet connection", Toast.LENGTH_LONG);
            return;
        }

        String leaderboardUrl = "http://dreamlo.com/lb/" + dreamlo_PUBLIC_KEY + "/json";
        Log.d(TAG, "Fetching leaderboard from URL: " + leaderboardUrl);

        StringRequest stringRequest = new StringRequest(Request.Method.GET, leaderboardUrl,
                response -> {
                    try {
                        Log.d(TAG, "Leaderboard response: " + response);
                        JSONObject jsonResponse = new JSONObject(response);

                        if (!jsonResponse.has("dreamlo") || jsonResponse.isNull("dreamlo")) {
                            leaderboardTextView.setText("No leaderboard data available.");
                            Log.e(TAG, "No 'dreamlo' object found in the response.");
                            return;
                        }

                        JSONObject dreamlo = jsonResponse.getJSONObject("dreamlo");

                        if (dreamlo.isNull("leaderboard")) {
                            leaderboardTextView.setText("No leaderboard entries yet.");
                            Log.e(TAG, "Leaderboard is null.");
                            return;
                        }

                        JSONObject leaderboard = dreamlo.getJSONObject("leaderboard");
                        Object entryObject = leaderboard.get("entry");
                        StringBuilder leaderboardText = new StringBuilder();

                        if (entryObject instanceof JSONArray) {
                            JSONArray entries = (JSONArray) entryObject;
                            for (int i = 0; i < entries.length(); i++) {
                                JSONObject entry = entries.getJSONObject(i);
                                String playerName = entry.getString("name");
                                int score = entry.getInt("score");
                                int timeInSeconds = entry.optInt("seconds", 0); // Assuming 'seconds' is the field in the JSON

                                leaderboardText.append(playerName)
                                        .append(" got a score of ")
                                        .append(score)
                                        .append(" in ")
                                        .append(timeInSeconds)
                                        .append(" seconds.\n");
                            }
                        } else if (entryObject instanceof JSONObject) {
                            JSONObject entry = (JSONObject) entryObject;
                            String playerName = entry.getString("name");
                            int score = entry.getInt("score");
                            int timeInSeconds = entry.optInt("seconds", 0); // Assuming 'seconds' is the field in the JSON

                            leaderboardText.append(playerName)
                                    .append(" got a score of ")
                                    .append(score)
                                    .append(" in ")
                                    .append(timeInSeconds)
                                    .append(" seconds.\n");
                        }

                        leaderboardTextView.setText(leaderboardText.toString());

                    } catch (JSONException e) {
                        leaderboardTextView.setText("Error parsing leaderboard data.");
                        showToast("Failed to parse leaderboard data: " + e.getMessage(), Toast.LENGTH_LONG);
                        Log.e(TAG, "Error parsing leaderboard response: ", e);
                    }
                },
                error -> {
                    leaderboardTextView.setText("Failed to fetch leaderboard. Please try again.");
                    showToast("Failed to fetch leaderboard. Network error: " + error.toString(), Toast.LENGTH_LONG);
                    Log.e(TAG, "Failed to fetch leaderboard: ", error);
                }
        );

        Volley.newRequestQueue(this).add(stringRequest);
    }

    private void submitScore(String playerName, int playerScore) {
        long timeTaken = (System.currentTimeMillis() - startTime) / 1000; // Calculate the time taken in seconds
        int randomNumber = (int) (Math.random() * 10); // Generates a random number between 0 and 999
        String submitScoreUrl = "http://dreamlo.com/lb/" + dreamlo_PRIVATE_KEY + "/add/" + playerName + randomNumber + "/" + playerScore + "/" + timeTaken;
        Log.d(TAG, "Submitting leaderboard from URL: " + submitScoreUrl);

        StringRequest stringRequest = new StringRequest(Request.Method.GET, submitScoreUrl,
                response -> {
                    showToast("Score submitted successfully!", Toast.LENGTH_LONG);
                    //fetchLeaderboard();  // Refresh leaderboard after successful submission
                },
                error -> {
                    showToast("Failed to submit score.", Toast.LENGTH_LONG);
                    Log.e(TAG, "Error submitting score: ", error);
                }
        );

        Volley.newRequestQueue(this).add(stringRequest);
    }

    private void setupGame() {
        score = 100;
        attemptsLeft = maxAttempts;
        emptyWordDisplay = new StringBuilder("_".repeat(targetWord.length()));
        wordDisplayTextView.setText(emptyWordDisplay.toString());
        gameStatusTextView.setText("Attempts Left: " + attemptsLeft);
        finalScoreTextView.setText("Final Score: " + score);
        timerDisplayTextView.setText("Time Taken: 0s");
        startTime = System.currentTimeMillis();
    }

    private void checkGuess() {
        String userInput = userInputEditText.getText().toString().trim();
        if (userInput.isEmpty()) {
            showToast("Please enter a guess", Toast.LENGTH_LONG);
            return;
        }

        // This will process a single letter guess or a full word guess
        if (userInput.length() == 1) {
            processSingleLetterGuess(userInput);
        } else {
            processFullWordGuess(userInput);
        }

        // Check if the guess matches the target word regardless of case
        if (userInput.equalsIgnoreCase(targetWord)) {
            long timeTaken = (System.currentTimeMillis() - startTime) / 1000;
            showToast("Congratulations! You guessed the word in " + timeTaken + " seconds.", Toast.LENGTH_LONG);
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String userName = sharedPreferences.getString(USER_NAME_KEY, "Player");
            //submitScore(userName, score);
            //resetGame();
        } else {
            showToast("Wrong guess! Try again!", Toast.LENGTH_SHORT);
        }

        updateGameUI();
        checkGameEnd();
    }

    private void processSingleLetterGuess(String userInput) {
        boolean found = false;
        for (int i = 0; i < targetWord.length(); i++) {
            if (targetWord.toLowerCase().charAt(i) == userInput.toLowerCase().charAt(0)) {
                emptyWordDisplay.setCharAt(i, userInput.charAt(0));
                found = true;
            }
        }
        if (found) {
            wordDisplayTextView.setText(emptyWordDisplay.toString());
        } else {
            score -= 10;
            attemptsLeft--;
        }
    }

    private void processFullWordGuess(String userInput) {
        if (userInput.equalsIgnoreCase(targetWord)) {
            long timeTaken = (System.currentTimeMillis() - startTime) / 1000;
            showToast("Congratulations! You guessed the word in " + timeTaken + " seconds.", Toast.LENGTH_LONG);
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String userName = sharedPreferences.getString(USER_NAME_KEY, "Player");
            submitScore(userName, score); // Submit the score
            resetGame();
        } else {
            score -= 10;
            attemptsLeft--;
        }
    }

    private void resetGame() {
        new android.os.Handler().postDelayed(() -> {
            Toast.makeText(MainActivity.this, "Restarting game...", Toast.LENGTH_SHORT).show();
            startGame(getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(USER_NAME_KEY, "Player"));
        }, 2000); // 2-second delay before resetting
    }

    private void requestLetterOccurrence() {
        String letter = userInputEditText.getText().toString().trim().toLowerCase();
        if (letter.isEmpty() || letter.length() > 1) {
            showToast("Please enter a single letter", Toast.LENGTH_LONG);
            return;
        }

        int occurrences = 0;
        for (int i = 0; i < targetWord.length(); i++) {
            if (targetWord.toLowerCase().charAt(i) == letter.charAt(0)) {
                occurrences++;
            }
        }

        if (score >= 5) {
            score -= 5;  // Deduct 5 points for using this feature
            showToast("The letter '" + letter + "' appears " + occurrences + " times.", Toast.LENGTH_LONG);
        } else {
            showToast("Not enough points to reveal letter occurrences.", Toast.LENGTH_LONG);
        }

        updateGameUI();
    }

    private void requestWordLength() {
        if (score >= 5) {
            score -= 5;  // Deduct 5 points for using this feature
            showToast("The word has " + targetWord.length() + " letters.", Toast.LENGTH_LONG);
        } else {
            showToast("Not enough points to reveal the word length.", Toast.LENGTH_LONG);
        }

        updateGameUI();
    }

    private void requestWordTip() {
        if (!isNetworkAvailable()) {
            showToast("No internet connection available.", Toast.LENGTH_LONG);
            return;
        }

        String url = "https://api.api-ninjas.com/v1/thesaurus?word=" + targetWord;

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONArray synonymsArray = jsonResponse.optJSONArray("synonyms");
                        if (synonymsArray != null && synonymsArray.length() > 0) {
                            String synonym = synonymsArray.getString(0);  // Fetch the first synonym
                            showToast("A similar word: " + synonym, Toast.LENGTH_LONG);
                        } else {
                            showToast("Sorry! No similar words found" , Toast.LENGTH_LONG);
                        }
                    } catch (JSONException e) {
                        showToast("Failed to process the response: " + e.getMessage(), Toast.LENGTH_LONG);
                    }
                },
                error -> {
                    showToast("Failed to fetch data. Please try again.", Toast.LENGTH_LONG);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("X-API-Key", ninjas_API_KEY);
                return headers;
            }
        };

        Volley.newRequestQueue(this).add(stringRequest);
    }

    private void updateGameUI() {
        gameStatusTextView.setText("Attempts Left: " + attemptsLeft);
        finalScoreTextView.setText("Final Score: " + score);
        userInputEditText.setText(""); // Clear input after each action
    }

    private void checkGameEnd() {
        if (attemptsLeft <= 0 || score <= 0) {
            showToast("Game Over! The word was: " + targetWord, Toast.LENGTH_LONG);
            resetGame();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private void showToast(String message, int length) {
        Toast.makeText(this, message, length).show();
    }
}
