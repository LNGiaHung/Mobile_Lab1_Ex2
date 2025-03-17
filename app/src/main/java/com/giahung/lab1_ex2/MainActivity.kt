package com.giahung.lab1_ex2

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.button.MaterialButton
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException

class MainActivity : AppCompatActivity() {
    private lateinit var inputText: EditText
    private lateinit var analyzeButton: MaterialButton
    private lateinit var resultText: TextView
    private lateinit var mainLayout: ConstraintLayout
    private val client = OkHttpClient()
    
    private val API_KEY = "AIzaSyD-SRUJ_SIRzkhhhHnqilr4Fb3RSDvcSu4"
    private val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputText = findViewById(R.id.inputText)
        analyzeButton = findViewById(R.id.analyzeButton)
        resultText = findViewById(R.id.resultText)
        mainLayout = findViewById(R.id.mainLayout)

        analyzeButton.setOnClickListener {
            val text = inputText.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Please enter some text to analyze", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            analyzeButton.isEnabled = false
            analyzeButton.text = "Analyzing..."
            resultText.text = "Processing..."
            mainLayout.setBackgroundColor(Color.WHITE)
            analyzeSentiment(text)
        }
    }

    private fun analyzeSentiment(text: String) {
        val prompt = """
            Analyze the sentiment of this text and respond with exactly one word: 
            either "positive", "negative", or "neutral". Text: "$text"
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", JSONArray().put(
                        JSONObject().put("text", prompt)
                    ))
                }
            ))
        }

        Log.d("API_REQUEST", "Request body: ${requestBody}")

        val request = Request.Builder()
            .url("$API_URL?key=$API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("API_ERROR", "API call failed", e)
                runOnUiThread {
                    val errorMessage = when (e) {
                        is UnknownHostException -> "No internet connection"
                        else -> "Error: ${e.message}"
                    }
                    resultText.text = errorMessage
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    resetButton()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = response.body?.string()
                    Log.d("API_RESPONSE", "Response: $responseBody")
                    
                    if (!response.isSuccessful) {
                        val errorMessage = try {
                            val errorJson = JSONObject(responseBody ?: "")
                            errorJson.optString("error", "API Error: ${response.code}")
                        } catch (e: Exception) {
                            "API Error: ${response.code}"
                        }
                        
                        Log.e("API_ERROR", "API returned error: $errorMessage")
                        runOnUiThread {
                            resultText.text = errorMessage
                            Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                            resetButton()
                        }
                        return@use
                    }
                    
                    val sentiment = extractSentiment(responseBody)
                    Log.d("API_SENTIMENT", "Extracted sentiment: $sentiment")
                    
                    runOnUiThread {
                        when (sentiment.toLowerCase()) {
                            "positive" -> {
                                mainLayout.setBackgroundColor(Color.rgb(200, 255, 200)) // Light green
                                resultText.text = "Positive sentiment 😊"
                            }
                            "negative" -> {
                                mainLayout.setBackgroundColor(Color.rgb(255, 200, 200)) // Light red
                                resultText.text = "Negative sentiment 😔"
                            }
                            "neutral" -> {
                                mainLayout.setBackgroundColor(Color.rgb(230, 230, 230)) // Light gray
                                resultText.text = "Neutral sentiment 😐"
                            }
                            else -> {
                                mainLayout.setBackgroundColor(Color.WHITE)
                                resultText.text = "Could not determine sentiment: $sentiment"
                                Toast.makeText(this@MainActivity, 
                                    "Unexpected response from API", 
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                        resetButton()
                    }
                }
            }
        })
    }

    private fun resetButton() {
        analyzeButton.isEnabled = true
        analyzeButton.text = "Analyze"
    }

    private fun extractSentiment(responseBody: String?): String {
        return try {
            val jsonResponse = JSONObject(responseBody ?: "")
            Log.d("API_PARSE", "Parsing response: $responseBody")
            
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return "No results found"
            }
            
            val content = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            content.trim()
        } catch (e: Exception) {
            Log.e("API_PARSE_ERROR", "Error parsing response", e)
            "Error: ${e.message}"
        }
    }
}
