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
    private val colorLightMidnight = Color.parseColor("#112240")
    private val colorBorder = Color.parseColor("#233554")
    private val colorTextGold = Color.parseColor("#E6B800")
    private val colorTextLight = Color.parseColor("#CCD6F6")
    private val colorTextDim = Color.parseColor("#8892B0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(colorMidnight)
            setPadding(100, 80, 100, 80)
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo_letras_horizontales)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 350).apply {
                bottomMargin = 120
            }
        }

        val emailInput = EditText(this).apply {
            hint = "Email"
            setHintTextColor(colorTextDim)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(colorLightMidnight)
                cornerRadius = 15f
            }
            setPadding(50, 40, 50, 40)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 30
            }
        }

        val passwordInput = EditText(this).apply {
            hint = "Contraseña"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHintTextColor(colorTextDim)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(colorLightMidnight)
                cornerRadius = 15f
            }
            setPadding(50, 40, 50, 40)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 100
            }
        }

        val loginBtn = Button(this).apply {
            text = "ENTRAR"
            setTextColor(colorMidnight)
            background = GradientDrawable().apply {
                setColor(colorTextGold)
                cornerRadius = 15f
            }
            stateListAnimator = null // Flat design
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
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150).apply {
                bottomMargin = 40
            }
        }

        val googleBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val shape = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 15f
                setStroke(2, colorBorder)
            }
            background = shape
            setPadding(0, 35, 0, 35)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 60
            }
            setOnClickListener {
                Toast.makeText(this@LoginActivity, "Próximamente: Login con Google", Toast.LENGTH_SHORT).show()
            }
            
            val icon = ImageView(this@LoginActivity).apply {
                setImageResource(android.R.drawable.ic_menu_gallery) // Placeholder for Google Icon
                layoutParams = LinearLayout.LayoutParams(40, 40).apply { rightMargin = 20 }
                setColorFilter(Color.WHITE)
            }
            val txt = TextView(this@LoginActivity).apply {
                text = "CONTINUAR CON GOOGLE"
                setTextColor(Color.WHITE)
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            addView(icon)
            addView(txt)
        }

        val registerLink = TextView(this).apply {
            text = "¿No tienes cuenta? Regístrate aquí"
            setTextColor(colorTextDim)
            textSize = 13f
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
