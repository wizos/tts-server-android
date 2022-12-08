package com.github.jing332.tts_server_android.ui

import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.github.jing332.tts_server_android.*
import com.github.jing332.tts_server_android.databinding.ActivityMainBinding
import com.github.jing332.tts_server_android.help.ServerConfig
import com.github.jing332.tts_server_android.service.TtsIntentService
import com.github.jing332.tts_server_android.ui.fragment.ServerLogFragment
import com.github.jing332.tts_server_android.ui.fragment.ServerWebFragment
import com.github.jing332.tts_server_android.ui.systts.TtsSettingsActivity
import com.github.jing332.tts_server_android.util.FileUtils.readAllText
import com.github.jing332.tts_server_android.util.MyTools
import com.github.jing332.tts_server_android.util.reduceDragSensitivity
import com.github.jing332.tts_server_android.util.setFadeAnim
import com.github.jing332.tts_server_android.util.toast


class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(
            layoutInflater
        )
    }

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        /* 左上角抽屉按钮 */
        val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, 0, 0)
        toggle.syncState()
        binding.drawerLayout.addDrawerListener(toggle)
        /* 版本名 */
        val tv = binding.nav.getHeaderView(0).findViewById<TextView>(R.id.nav_header_subtitle)
        tv.text = BuildConfig.VERSION_NAME

        binding.nav.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_systts_settings ->
                    startActivity(Intent(this, TtsSettingsActivity::class.java))
                R.id.nav_settings ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                R.id.nav_killBattery ->
                    killBattery()
                R.id.nav_checkUpdate ->
                    MyTools.checkUpdate(this)
                R.id.nav_about ->
                    showAboutDialog()

            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        binding.viewPager.reduceDragSensitivity(8)
        binding.viewPager.adapter = FragmentAdapter(this)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.bottomNavigationView.menu.getItem(position).isChecked = true
            }
        })
        binding.bottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.menu_serverLog -> binding.viewPager.setCurrentItem(0, true)
                R.id.menu_serverWeb -> {
                    binding.viewPager.setCurrentItem(1, true)
                }
            }
            true
        }

        MyTools.checkUpdate(this)
    }


    /*右上角更多菜单*/
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    /* 准备菜单 */
    @SuppressLint("RestrictedApi")
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
        }

        /* 从配置文件读取并更新isWakeLock */
        val item = menu?.findItem(R.id.menu_wakeLock)
        item?.isChecked = ServerConfig.isWakeLockEnabled

        return super.onCreateOptionsMenu(menu)
    }

    /*菜单点击事件*/
    @Suppress("DEPRECATION")
    @SuppressLint("BatteryLife")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_openWeb -> { /* {打开网页版} 按钮 */
                if (TtsIntentService.instance?.isRunning == true) {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data =
                        Uri.parse("http://localhost:${TtsIntentService.instance?.cfg?.port}")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                } else {
                    toast(R.string.please_start_service)
                }
            }
            R.id.menu_clearWebData -> {
                WebView(applicationContext).apply {
                    clearCache(true)
                    clearFormData()
                    clearSslPreferences()
                }
                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
                WebStorage.getInstance().deleteAllData()
                toast(R.string.cleared)
            }
            R.id.menu_setToken -> {
                val token = ServerConfig.token

                val editText = EditText(this)
                editText.setText(token)
                AlertDialog.Builder(this).setTitle(getString(R.string.set_token)).setView(editText)
                    .setPositiveButton(
                        android.R.string.ok
                    ) { _, _ ->
                        val text = editText.text.toString()
                        if (text != token) {
                            toast(getString(R.string.token_set_to) + text.ifEmpty { "空" })
                            ServerConfig.token = text
                        }
                    }.setNegativeButton(R.string.reset) { _, _ ->
                        ServerConfig.token = ""
                        toast(getString(R.string.ok_reset))
                    }.setFadeAnim().show()
            }
            R.id.menu_wakeLock -> { /* 唤醒锁 */
                item.isChecked = !item.isChecked /* 更新选中状态 */
                ServerConfig.isWakeLockEnabled = item.isChecked
                toast(R.string.restart_service_to_update)
            }
            R.id.menu_shortcut -> {
                MyTools.addShortcut(
                    this,
                    getString(R.string.app_switch),
                    "server_switch",
                    R.drawable.ic_switch,
                    Intent(this, ScSwitchActivity::class.java)
                )
            }
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun showAboutDialog() {
        val tv = TextView(this)
        tv.movementMethod = LinkMovementMethod()
        tv.text = Html.fromHtml(resources.openRawResource(R.raw.abort_info).readAllText())
        tv.gravity = Gravity.CENTER /* 居中 */
        tv.setPadding(25, 25, 25, 25)
        AlertDialog.Builder(this).setTitle(R.string.about).setView(tv)
            .setMessage("本应用界面使用Kotlin开发，底层服务由Go开发.")
            .setFadeAnim().show()
    }

    @SuppressLint("BatteryLife")
    private fun killBattery() {
        val intent = Intent()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                toast(R.string.added_background_whitelist)
            } else {
                try {
                    intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    toast(R.string.system_not_support_please_manual_set)
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.viewPager.currentItem == 1) {
            if (!webFragment.onBackKeyDown())
                binding.viewPager.setCurrentItem(0, true)

            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    val logFragment = ServerLogFragment()
    val webFragment = ServerWebFragment()

    inner class FragmentAdapter(fragmentActivity: FragmentActivity) :
        FragmentStateAdapter(fragmentActivity) {
        private val fragmentList = arrayListOf(logFragment, webFragment)
        override fun getItemCount(): Int {
            return fragmentList.size
        }

        override fun createFragment(position: Int): Fragment {
            return fragmentList[position]
        }
    }
}

