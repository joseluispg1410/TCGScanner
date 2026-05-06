package com.example.tcgscanner

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val colorMidnight = Color.parseColor("#0A192F")
    private val colorHeaderBg = Color.parseColor("#172A45")
    private val colorTextGold = Color.parseColor("#E6B800")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(colorMidnight)
            setPadding(80, 80, 80, 80)
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo_letras_horizontales)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400).apply {
                bottomMargin = 100
            }
        }

        val emailInput = EditText(this).apply {
            hint = "Email"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(colorHeaderBg)
                cornerRadius = 20f
                setStroke(2, Color.GRAY)
            }
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 40
            }
        }

        val passwordInput = EditText(this).apply {
            hint = "Contraseña"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(colorHeaderBg)
                cornerRadius = 20f
                setStroke(2, Color.GRAY)
            }
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 80
            }
        }

        val loginBtn = Button(this).apply {
            text = "ENTRAR"
            setTextColor(colorMidnight)
            setBackgroundColor(colorTextGold)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setOnClickListener {
                val email = emailInput.text.toString()
                val password = passwordInput.text.toString()
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    performLogin(email, password)
                } else {
                    Toast.makeText(this@LoginActivity, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
                }
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 40
            }
        }

        val googleBtn = Button(this).apply {
            text = "CONTINUAR CON GOOGLE"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4285F4"))
            setOnClickListener {
                Toast.makeText(this@LoginActivity, "Próximamente: Login con Google", Toast.LENGTH_SHORT).show()
            }
        }

        val registerLink = TextView(this).apply {
            text = "¿No tienes cuenta? Regístrate aquí"
            setTextColor(colorTextGold)
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 0)
            setOnClickListener {
                val email = emailInput.text.toString()
                val password = passwordInput.text.toString()
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    performRegister(email, password)
                } else {
                    Toast.makeText(this@LoginActivity, "Introduce email y contraseña para registrarte", Toast.LENGTH_SHORT).show()
                }
            }
        }

        root.addView(logo)
        root.addView(emailInput)
        root.addView(passwordInput)
        root.addView(loginBtn)
        root.addView(googleBtn)
        root.addView(registerLink)

        setContentView(root)
    }

    private fun performLogin(email: String, password: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.login(AuthRequest(email, password))
                if (response.isSuccessful && response.body()?.user_id != null) {
                    val userId = response.body()!!.user_id!!
                    
                    // Guardar sesión
                    getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit {
                        putInt("user_id", userId)
                        putString("user_email", email)
                    }

                    Toast.makeText(this@LoginActivity, "Bienvenido", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "Error: Credenciales inválidas", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Fallo de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performRegister(email: String, password: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.register(AuthRequest(email, password))
                if (response.isSuccessful) {
                    Toast.makeText(this@LoginActivity, "Usuario registrado. Ya puedes entrar.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@LoginActivity, "Error al registrar: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Fallo de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
