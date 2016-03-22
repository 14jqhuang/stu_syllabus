package com.hjsmallfly.syllabus.activities;

import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.hjsmallfly.syllabus.adapters.BannerPagerAdapter;
import com.hjsmallfly.syllabus.customviews.WrapContentHeightViewPager;
import com.hjsmallfly.syllabus.helpers.BannerGetter;
import com.hjsmallfly.syllabus.helpers.DownloadTask;
import com.hjsmallfly.syllabus.helpers.InternetLogin;
import com.hjsmallfly.syllabus.helpers.LessonPullTask;
import com.hjsmallfly.syllabus.helpers.StringDataHelper;
import com.hjsmallfly.syllabus.helpers.UpdateHelper;
import com.hjsmallfly.syllabus.helpers.WebApi;
import com.hjsmallfly.syllabus.interfaces.BannerHandler;
import com.hjsmallfly.syllabus.interfaces.FileDownloadedHandle;
import com.hjsmallfly.syllabus.interfaces.LessonHandler;
import com.hjsmallfly.syllabus.interfaces.TokenGetter;
import com.hjsmallfly.syllabus.interfaces.UpdateHandler;
import com.hjsmallfly.syllabus.parsers.ClassParser;
import com.hjsmallfly.syllabus.helpers.FileOperation;
import com.hjsmallfly.syllabus.syllabus.Banner;
import com.hjsmallfly.syllabus.syllabus.R;
import com.hjsmallfly.syllabus.syllabus.SyllabusVersion;
import com.hjsmallfly.syllabus.widget.FixedSpeedScroller;
import com.umeng.analytics.MobclickAgent;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends AppCompatActivity implements View.OnClickListener, UpdateHandler, LessonHandler, TokenGetter, Spinner.OnItemSelectedListener, BannerHandler, FileDownloadedHandle {
    public static Object[] weekdays_syllabus_data;     // 用于向显示课表的activity传递数据
    public static String info_about_syllabus;
    public static final String USERNAME_FILE = "username.txt";
    public static final String PASSWORD_FILE = "password.txt";

    public static String syllabus_json_data;

    // 用户的token数据
    public static String token = "";

    // 用于和其他activity共享的数据
    public static String cur_year_string;
    public static int cur_semester;
    public static String cur_username;
    public static String cur_password;
    public static int initial_week = -1;
    public static String initial_date = null;

    // 控件及常量
    public static final String TAG = "POSTTEST";
    public static String[] YEARS;// = {"2012-2013", "2013-2014", "2014-2015", "2015-2016", "2016-2017", "2017-2018"};
    public static final String[] SEMESTER = new String[]{"SPRING", "SUMMER", "AUTUMN"};

    public static final String[] SEMESTER_CHINESE = new String[]{"春季学期", "夏季学期", "秋季学期"};


    private int position = -1;  // 用于决定保存的文件名
//    private int semester;    // 用于决定保存的文件名

    private WrapContentHeightViewPager viewPager;

    //    private EditText address_edit;  // 服务器地址
//    private EditText username_edit;
//    private EditText password_edit;
//    private ListView syllabus_list_view;    // 用于显示所有课表的list_view

    private Spinner year_spinner;
    private Spinner semester_spinner;

    // ----------功能区域-----------
    private LinearLayout query_button_layout;
    private LinearLayout login_wifi_button_layout;
    private LinearLayout oa_button_layout;
    private LinearLayout school_activity_button_layout;
    private LinearLayout logout_button_layout;
    private LinearLayout grade_button_layout;
    private LinearLayout exam_button_layout;
    // ----------功能区域-----------

    private EditText debug_ip_edit;

    // 如果已经显示过默认课表就没必要再显示了
    private boolean has_showed_default = false;
    private boolean has_checked_update = false;

    private UpdateHelper updateHelper;

    private String banner_json_data;
    private List<Banner> bannerList;
    private List<File> fileList;
    private BannerPagerAdapter bannerPagerAdapter;


    private boolean hasDisplayed = false;

    private boolean autoScroll = true;
//    private int banner_index;   // 循环播放的图片的下标

    /**
     * 强制显示菜单
     */
    private void getOverflowMenu() {
        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class
                    .getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 创建主界面
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);  // 加载主布局

        // 检查是否有登录
        if (!has_saved_user()) {
            Intent loginIntent = new Intent(this, LoginActivity.class);
            startActivity(loginIntent);
        }

        // 第一次强行显示那个界面
        if (has_saved_user() && !FileOperation.hasFile(this, LoginActivity.VERIFY_FILE_NAME)) {
            String[] user_info = FileOperation.load_user(this, USERNAME_FILE, PASSWORD_FILE);
            if (user_info != null) {
                LoginActivity.setted_username = user_info[0];
                LoginActivity.setted_password = user_info[1];
                Intent loginIntent = new Intent(this, LoginActivity.class);
                startActivity(loginIntent);
            }

        }

        // 强制显示菜单
        getOverflowMenu();

        YEARS = StringDataHelper.generate_years(4);  // 生成4年的选项
        getAllViews();
        setupViews();

        // 设置web service 的默认地址
        WebApi.set_server_address(getString(R.string.server_ip));

        // 加载图片
        getLatestBannerInfo();

        // 设置本地缓存的token
        get_local_token();

        // 检查更新
        if (!has_checked_update)
            check_update();
        if (!has_showed_default)
            load_default_syllabus();

    }


    // 友盟的统计功能
    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
    }

    public void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
    }

    private void getAllViews() {

        viewPager = (WrapContentHeightViewPager) findViewById(R.id.bannerViewPager);

        try {
            Field field = ViewPager.class.getDeclaredField("mScroller");
            field.setAccessible(true);
            FixedSpeedScroller scroller = new FixedSpeedScroller(viewPager.getContext(),
                    new AccelerateInterpolator());
            field.set(viewPager, scroller);
            scroller.setmDuration(400);
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }

//        address_edit = (EditText) findViewById(R.id.address_edit);
//        username_edit = (EditText) findViewById(R.id.username_edit);
//        password_edit = (EditText) findViewById(R.id.password_edit);
//        syllabus_list_view = (ListView) findViewById(R.id.syllabus_list_view);

        year_spinner = (Spinner) findViewById(R.id.year_spinner);
        semester_spinner = (Spinner) findViewById(R.id.semester_spinner);

        // ----------功能区域-----------
        query_button_layout = (LinearLayout) findViewById(R.id.query_syllabus_button);
        login_wifi_button_layout = (LinearLayout) findViewById(R.id.login_wifi_button);
        oa_button_layout = (LinearLayout) findViewById(R.id.query_oa_button);
        school_activity_button_layout = (LinearLayout) findViewById(R.id.school_activity_button);
        grade_button_layout = (LinearLayout) findViewById(R.id.query_grade_text_view);
        exam_button_layout = (LinearLayout) findViewById(R.id.query_exam_text_view);
        logout_button_layout = (LinearLayout) findViewById(R.id.back_to_login_button);
        // ----------功能区域-----------


        debug_ip_edit = (EditText) findViewById(R.id.debug_ip_edit);
    }

    private String[] load_saved_user() {
        return FileOperation.load_user(this, USERNAME_FILE, PASSWORD_FILE);
    }

    private boolean has_saved_user() {
        return FileOperation.hasFile(this, USERNAME_FILE);
    }

    private void setupViews() {
//        YearSemesterChooseParser list_adapter = new YearSemesterChooseParser(this);
//        syllabus_list_view.setAdapter(list_adapter);

        year_spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, YEARS));
        semester_spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, SEMESTER_CHINESE));

        // 读取用户
        final String[] user = load_saved_user();
        if (user != null) {
//            username_edit.setText(user[0]);
//            password_edit.setText(user[1]);
            // 用这个变量来记录登录了的账号密码
            cur_username = user[0];
            cur_password = user[1];

            if (user[0].equals("14xfdeng")) {
                // 开启debug模式
                debug_ip_edit.setVisibility(View.VISIBLE);

            } else {
                debug_ip_edit.setVisibility(View.GONE);
            }
        } else
            debug_ip_edit.setVisibility(View.GONE);


        // 选项卡

        // listener
        query_button_layout.setOnClickListener(this);

        login_wifi_button_layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                String username = username_edit.getText().toString().trim();
//                String password = password_edit.getText().toString().trim();
//                if (username.isEmpty() || password.isEmpty()) {
//                    Toast.makeText(MainActivity.this, "请输入账号和密码", Toast.LENGTH_SHORT).show();
//                    return;
//                }
                InternetLogin.login_to_internet(MainActivity.this, cur_username, cur_password);
            }
        });

        oa_button_layout.setOnClickListener(this);
        school_activity_button_layout.setOnClickListener(this);
        grade_button_layout.setOnClickListener(this);
        exam_button_layout.setOnClickListener(this);
        logout_button_layout.setOnClickListener(this);

        semester_spinner.setOnItemSelectedListener(this);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.check_update_action) {
            // 友盟
            MobclickAgent.onEvent(this, "Check_Update");
            Intent update_activity = new Intent(this, UpdateActivity.class);
            startActivity(update_activity);
            return true;
        }

        if (id == R.id.delete_default_syllabus) {
            if (delete_default_syllabus())
                Toast.makeText(MainActivity.this, "清除了默认课表的设置", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(MainActivity.this, "清除默认课表出错", Toast.LENGTH_SHORT).show();
            return true;
        }

        if (id == R.id.about_us_action) {
            // 友盟
            MobclickAgent.onEvent(this, "Setting_Aboutus");
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.help_action) {
            Intent intent = new Intent(this, HelpActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private boolean delete_default_syllabus() {
        if (FileOperation.hasFile(this, SyllabusActivity.DEFAULT_SYLLABUS_FILE))
            return FileOperation.delete_file(this, SyllabusActivity.DEFAULT_SYLLABUS_FILE);
        return true;
    }

    private void load_default_syllabus() {
        String default_file_name = FileOperation.read_from_file(this, SyllabusActivity.DEFAULT_SYLLABUS_FILE);
        if (default_file_name != null) {
            if (FileOperation.hasFile(this, default_file_name)) {
//                Toast.makeText(MainActivity.this, "存在文件: " + default_file_name, Toast.LENGTH_SHORT).show();
                String json_data = FileOperation.read_from_file(this, default_file_name);
                if (json_data != null) {

                    // 设置一些相关信息
                    String[] info = default_file_name.split("_");
                    cur_username = info[0];
//                    if (!cur_username.equals(username_edit.getText().toString()))
                    // 说明用户已经登录了其他账号
//                        return;
                    cur_year_string = info[1];
                    cur_semester = StringDataHelper.semester_to_int(info[2]);
//                    Log.d("default", cur_year_string);

                    // 将年份和学期的选项设置为默认状态
                    semester_spinner.setSelection(StringDataHelper.semester_to_selection_index(cur_semester));
                    for (int i = 0; i < YEARS.length; ++i)
                        if (cur_year_string.equals(YEARS[i])) {
                            year_spinner.setSelection(i);
                            position = i;
                        }

                    info_about_syllabus = cur_username + " " + cur_year_string + " " + info[2];

                    has_showed_default = true;

//                    String[] week_info = get_week_info();
//                    if (week_info == null){
//                        set_week_info(syllabus_json_data, false);
//                    }else{
//                        MainActivity.initial_date = week_info[0];
//                        MainActivity.initial_week = Integer.parseInt(week_info[1]);
//                        // 本地课表文件里面存的token可能是过期的.
//                        parse_and_display(syllabus_json_data, false);
//                    }
                }
            }
        }
    }

    private void set_cur_semester_with_spinner(int selection_id) {
        switch (selection_id) {
            case 0:
//                semester = 2;
                cur_semester = 2;
                break;
            case 1:
//                semester = 3;
                cur_semester = 3;
                break;
            case 2:
//                semester = 1;
                cur_semester = 1;
                break;
            default:
                Log.d(TAG, "maybe there is a typo  in submit(int, int)");
                break;
        }
    }

    private boolean set_week_info(final String json_data, final boolean update_local_token) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);
        builder.setTitle("设定当前周数(目前按照周一作为第一天)");

        final NumberPicker numberPicker = new NumberPicker(this);
        numberPicker.setMaxValue(16);
        numberPicker.setMinValue(1);
        numberPicker.setValue(1);
        builder.setView(numberPicker);

        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int week = numberPicker.getValue();
                Calendar calendar = Calendar.getInstance();
                int year = calendar.get(Calendar.YEAR);
                int month = calendar.get(Calendar.MONTH);   // 0-11
                int day = calendar.get(Calendar.DAY_OF_MONTH);
                String date_string = year + "/" + month + "/" + day;
                String content = date_string + "," + week;

                String filename = FileOperation.generate_week_file(cur_username, cur_year_string, cur_semester + "");
                if (FileOperation.save_to_file(MainActivity.this, filename, content)) {
                    Toast.makeText(MainActivity.this, "设定当前周数为 " + week, Toast.LENGTH_SHORT).show();
                    MainActivity.initial_week = week;
                    MainActivity.initial_date = date_string;
                } else {
                    Toast.makeText(MainActivity.this, "设置周数出错", Toast.LENGTH_SHORT).show();
                }
                parse_and_display(json_data, update_local_token);
                dialog.dismiss();
            }
        });

        builder.setCancelable(false);
        builder.create().show();
        return true;
    }

    /**
     * @return 字符串数组，[0] - date; [1] - 周数 或者 null
     */
    private String[] get_week_info() {
        String week_filename = FileOperation.generate_week_file(cur_username, cur_year_string, cur_semester + "");
        if (FileOperation.hasFile(this, week_filename)) {
            String content = FileOperation.read_from_file(this, week_filename);
            if (content != null) {
                return content.split(",");
            }
            return null;
        }
        return null;
    }

    /**
     * @param year_index             年份下标
     * @param semester_spinner_index 下拉菜单的选中项, 注意这个并不对应学分制所需要的学期参数
     */
    private void submit_query_request(int year_index, int semester_spinner_index) {
        this.position = year_index;
//        String username = username_edit.getText().toString();
//        cur_username = username;

        String years = YEARS[year_index];  // 点击到列表的哪一项
        cur_year_string = years;    // 用于共享目的

//        String password = password_edit.getText().toString();
//        cur_password = password;

        // 更新一下 服务器的地址
        WebApi.set_server_address(debug_ip_edit.getText().toString());

        // 读取之前存的 token
        get_local_token();

        set_cur_semester_with_spinner(semester_spinner_index);

        info_about_syllabus = cur_username + " " + years + " " + StringDataHelper.semester_to_string(cur_semester);
        // 先判断有无之前保存的文件
//        String filename = username + "_" + years + "_" + semester;
        String filename = StringDataHelper.generate_syllabus_file_name(cur_username, years, cur_semester, "_");
        String json_data = FileOperation.read_from_file(MainActivity.this, filename);
        if (json_data != null) {
            // 检查有没有设置周数
            String[] week_info = get_week_info();
            if (week_info == null) {
                // 提示用户设定周数
                set_week_info(json_data, false);

            } else {
                // 本地的文件里面的token可能是过期的
                initial_date = week_info[0];
                initial_week = Integer.parseInt(week_info[1]);
                parse_and_display(json_data, false);
            }
            return;
        }

        Toast.makeText(MainActivity.this, "正在获取课表信息", Toast.LENGTH_SHORT).show();


        // 禁用按钮
        query_button_layout.setEnabled(false);

//            {"SPRING", "SUMMER", "AUTUMN"}

        HashMap<String, String> postData = new HashMap<>();
        postData.put("username", cur_username);
        postData.put("password", cur_password);
        postData.put("submit", "query");
        postData.put("years", years);
        postData.put("semester", cur_semester + "");
//        Log.d(TAG, "onClick");

        LessonPullTask task = new LessonPullTask(WebApi.get_server_address() + getString(R.string.syllabus_get_api), this);
        task.execute(postData);


//        syllabusGetter.execute(postData);
    }

    private void check_update() {
        if (updateHelper == null)
            updateHelper = new UpdateHelper(this, this);
        updateHelper.check_for_update();
        has_checked_update = true;
    }


    @Override
    public void deal_with_update(int flag, final SyllabusVersion version) {
        if (flag == UpdateHandler.EXIST_UPDATE) {
            // 存在更新的话
            Toast.makeText(MainActivity.this, "有新版本啦", Toast.LENGTH_SHORT).show();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("发现新版本, 是否更新?");
            builder.setMessage("描述:\n" + version.description);
            builder.setPositiveButton("更新", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
//                    Intent update_activity = new Intent(MainActivity.this, UpdateActivity.class);
//                    startActivity(update_activity);
                    updateHelper.download(version.dowload_address, version);
                    Toast.makeText(MainActivity.this, "开始下载", Toast.LENGTH_SHORT).show();
//                    dialog.dismiss();
                }
            });
            builder.setNegativeButton("稍后", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.create().show();
        }
    }


    @Override
    public void deal_with_lessons(String raw_data) {

        // 恢复按钮
        query_button_layout.setEnabled(true);

        if (raw_data.isEmpty()) {
            Toast.makeText(MainActivity.this, "没能成功获取课表数据", Toast.LENGTH_SHORT).show();
            return;
        }
        // 统计用户登陆
        MobclickAgent.onProfileSignIn(MainActivity.cur_username);

        FileOperation.save_user(this, USERNAME_FILE, PASSWORD_FILE, cur_username, cur_password);

        // 从网络拉过来的数据中 token 肯定是新的, 所以需要更新本地的token
        String[] week_info = get_week_info();
        if (week_info == null && !raw_data.contains("ERROR")) {
            set_week_info(raw_data, true);
        } else {
            if (week_info != null) {
                MainActivity.initial_date = week_info[0];
                MainActivity.initial_week = Integer.parseInt(week_info[1]);
            }
            parse_and_display(raw_data, true);
        }

    }

    private void parse_and_display(String json_data, boolean update_local_token) {
//        if (classParser == null)
        // 每次用新的classParser [暂时这样修复这个BUG]
        ClassParser classParser = new ClassParser(this, this);
        if (classParser.parseJSON(json_data, update_local_token)) {
            classParser.inflateTable();     // 用数据填充课表
            MainActivity.weekdays_syllabus_data = classParser.weekdays_syllabus_data;
//            MainActivity.weekends_syllabus_data = classParser.weekend_classes;
//                    Log.d(TAG, "established adapter");

            // 保存文件 命名格式: name_years_semester
//            String username = ((EditText) MainActivity.this.findViewById(R.id.username_edit)).getText().toString();
//                    String filename = username + "_" + YEARS[position] + "_"
//                            + semester;
            // 保存文件 格式是: 14xfdeng_2014-2015_autumn
            String filename = StringDataHelper.generate_syllabus_file_name(cur_username, YEARS[position], cur_semester, "_");
            if (FileOperation.save_to_file(MainActivity.this, filename, json_data)) {
//                        Toast.makeText(MainActivity.this, "成功保存文件 " + filename, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "saved file " + filename);
                // 保存用户文件
                FileOperation.save_user(MainActivity.this, USERNAME_FILE, PASSWORD_FILE, cur_username, cur_password);

                // 读取token
                get_local_token();

                Intent syllabus_activity = new Intent(MainActivity.this, SyllabusActivity.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    startActivity(syllabus_activity,
                            ActivityOptions.makeSceneTransitionAnimation(MainActivity.this).toBundle());
                } else {
                    startActivity(syllabus_activity);
                }
//                    Toast.makeText(MainActivity.this, "读取课表成功哟~~~~", Toast.LENGTH_SHORT).show();

            }

        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.query_syllabus_button:
                query_syllabus();
                break;
            case R.id.query_oa_button:
                Intent intent = new Intent(this, OAActivity.class);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    View img =  findViewById(R.id.oa_img);
//                    View text = findViewById(R.id.oa_text);
                    startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(
                            MainActivity.this,
                            Pair.create(img,"oa_logo_share")).toBundle());
                } else {
                    startActivity(intent);
                }

                break;
            case R.id.school_activity_button:
                Intent global_discuss_intent = new Intent(this, GlobalDiscussActivity.class);
                startActivity(global_discuss_intent);
                break;
            case R.id.query_grade_text_view:
                Intent grade_intent = new Intent(this, GradeActivity.class);
                startActivity(grade_intent);
                break;
            case R.id.query_exam_text_view:
                // 设定一些参数
                cur_year_string = YEARS[year_spinner.getSelectedItemPosition()];
                set_cur_semester_with_spinner(semester_spinner.getSelectedItemPosition());
                Intent exam_intent = new Intent(this, ExamActivity.class);
                startActivity(exam_intent);
                break;
            case R.id.back_to_login_button:
                if (cur_username != null && cur_password != null) {
                    LoginActivity.setted_username = cur_username;
                    LoginActivity.setted_password = cur_password;
                    Intent login_intent = new Intent(this, LoginActivity.class);
                    startActivity(login_intent);
                }
                break;
            default:
                break;
        }
    }

    private void query_syllabus() {
//        if (username_edit.getText().toString().trim().isEmpty() ||
//                password_edit.getText().toString().trim().isEmpty()){
//            Toast.makeText(MainActivity.this, "请输入账号和密码", Toast.LENGTH_SHORT).show();
//            return;
//        }
        int year_index = year_spinner.getSelectedItemPosition();
        int semester_index = semester_spinner.getSelectedItemPosition();
        submit_query_request(year_index, semester_index);
    }


    @Override
    public void get_token(String token) {
        MainActivity.token = token;
        boolean saved =
                FileOperation.save_to_file(this, StringDataHelper.generate_token_file_name(cur_username), token);
        if (!saved) {
            Toast.makeText(MainActivity.this, "保存Token文件失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 获取本地存储的token
     */
    public void get_local_token() {
        String filename = StringDataHelper.generate_token_file_name(cur_username);
        if (FileOperation.hasFile(this, filename)) {
            MainActivity.token = FileOperation.read_from_file(this, filename);
//            Toast.makeText(MainActivity.this, "成功读取Token " + token, Toast.LENGTH_SHORT).show();
        } else
            MainActivity.token = "";

    }

    public static void set_local_token(Context use_for_file_context) {
        String filename = StringDataHelper.generate_token_file_name(cur_username);
        if (FileOperation.hasFile(use_for_file_context, filename)) {
            MainActivity.token = FileOperation.read_from_file(use_for_file_context, filename);
//            Toast.makeText(MainActivity.this, "成功读取Token " + token, Toast.LENGTH_SHORT).show();
        } else
            MainActivity.token = "";

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        set_cur_semester_with_spinner(position);
//        Toast.makeText(MainActivity.this, "position: " + position + " cur_semester = " + cur_semester, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    /**
     * 循环播放图片
     */
    private void auto_scroll() {
        Thread scroll_thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("switch", "thread started!");
                while (autoScroll) {
                    try {
                        Thread.sleep(2500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            int count = bannerPagerAdapter.getCount();
                            if (count != 0) {
                                int next = (viewPager.getCurrentItem() + 1) % count;
                                viewPager.setCurrentItem(next, true);
                            }

                        }
                    });
                }
                Log.d("switch", "thread quited");
            }
        });
        scroll_thread.start();
    }

    private boolean isBannersCached() {
        return FileOperation.hasFile(this, getString(R.string.BANNER_CACHED_FILE));
    }

    /**
     * 从服务器请求最新的banner信息(json)
     */
    private void getLatestBannerInfo() {
        if (isBannersCached()) {
//             先读取缓存文件
            Log.d("banner", "优先读取缓存好了的文件");
            String banner_json = FileOperation.read_from_file(this, getString(R.string.BANNER_CACHED_FILE));
            if (banner_json != null) {
                // 设置类成员 banner_json_data
                this.banner_json_data = banner_json;
                List<Banner> banners = Banner.parse(banner_json);
                List<String> filenames = Banner.toFilenames(banners);
                List<File> files = loadCachedBannerFile("Syllabus", filenames);
                if (files.size() > 0) {
                    Log.d("banner", "使用缓存文件");
                    display_banners(files);
                } else {  // 本地有缓存记录,但是图片被用户删除了
                    Log.d("banner", "缓存文件被删除了");
                    // 删除记录缓存的文件, 让app从服务器重新下载图片
                    FileOperation.delete_file(this, getString(R.string.BANNER_CACHED_FILE));
                }
            }
        }
        BannerGetter bannerGetter = new BannerGetter(this);
        Log.d("banner", "从服务器拉取新的banner信息");
        bannerGetter.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, WebApi.get_server_address() + getString(R.string.get_banner_api));
    }

    private void display_banners(List<File> files) {
        if (files != null) {
            if (fileList == null)
                fileList = new ArrayList<>();
            fileList.addAll(files);

            if (bannerPagerAdapter == null) {
                bannerPagerAdapter = new BannerPagerAdapter(this, fileList);
                if (viewPager.getAdapter() == null)
                    viewPager.setAdapter(bannerPagerAdapter);
                else
                    bannerPagerAdapter.notifyDataSetChanged();
            } else {
                bannerPagerAdapter.notifyDataSetChanged();
            }
            // 更新本地的banner缓存文件
            if (FileOperation.save_to_file(this, getString(R.string.BANNER_CACHED_FILE), this.banner_json_data)) {
                Log.d("banner", "成功缓存banner文件");
            } else {
                Log.d("banner", "失败缓存banner文件");
            }
            // 开启循环播放图片
            if (!hasDisplayed)
                auto_scroll();
//            Toast.makeText(MainActivity.this, files.toString(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(MainActivity.this, "文件下载失败,请查看日志", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void handle_downloaded_file(List<File> files) {
        // 清空一些数据
        if (fileList != null) {
            Log.d("banner", "清空已经显示的图片");
            Log.d("switch", "重新下载了图片文件");
//            viewPager.requestLayout();
            files.clear();
        }
        display_banners(files);
    }


    private void set_banners() {
        Log.d("banner", "setting_banners");
        if (bannerList.size() == 0) {
            Log.d("banner", "bannerList 的 size 为0");
            return;
        }

        // 是否使用之前缓存过的图片
        boolean use_cached_files = false;

        // 决定使用本地缓存的图片或者从网络上下载
        if (FileOperation.hasFile(this, getString(R.string.BANNER_CACHED_FILE))) {
            // 如果已经有缓存文件了,说明已经显示了本地缓存的文件了
            long latestTimestamp = bannerList.get(0).getTimestamp();
            Log.d("banner", "最新的时间戳是: " + latestTimestamp + "");
            String local_banner_json_data = FileOperation.read_from_file(this, getString(R.string.BANNER_CACHED_FILE));
            long local_timestamp = Banner.getTimestap(local_banner_json_data);
            Log.d("banner", "缓存的时间戳是: " + local_timestamp + "");
            if (local_timestamp == latestTimestamp) {
                use_cached_files = true;
            }
        }

        // 转换banner数据
        List<String> urls = Banner.toUrls(this.bannerList);
        List<String> filenames = Banner.toFilenames(this.bannerList);

        // 去读缓存的情况已经在之前处理过了
        if (!use_cached_files) {
            // 从网络上下载新的图片
            Log.d("banner", "没有缓存图片, 需要重新下载新的图片");
            DownloadTask downloadTask = new DownloadTask(urls, "Syllabus", filenames, this, 4000);
            downloadTask.execute();
        }

    }

    private List<File> loadCachedBannerFile(String directory, List<String> filenames) {
        List<File> files = new ArrayList<>();
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Log.d("banner", "sdcard not mounted");
            return files;
        }
        // sd 卡根目录
        String sdCardRoot = Environment.getExternalStorageDirectory() + File.separator;
        Log.d("banner", "sdCardRoot is: " + sdCardRoot);
        // 文件存储目录
        String file_save_path = sdCardRoot + directory;
        Log.d("banner", "file_save_path is: " + file_save_path);

        for (int i = 0; i < filenames.size(); ++i) {
            File file = new File(file_save_path, filenames.get(i));
            if (file.exists())
                files.add(file);
        }

        return files;
    }

    @Override
    public void handle_get_response(String result) {
        if (result.isEmpty()) {
//            Toast.makeText(MainActivity.this, "未能成功获取图片", Toast.LENGTH_SHORT).show();
            Log.d("banner", "未能成功获取图片");
            return;
        }
        this.banner_json_data = result;
        this.bannerList = Banner.parse(result);
        if (bannerList != null && bannerList.size() > 0) {
//            Toast.makeText(MainActivity.this, bannerList.get(0).getUrl(), Toast.LENGTH_SHORT).show();
            set_banners();
        } else {
//            Toast.makeText(MainActivity.this, "服务器没有资源,或者解析失败", Toast.LENGTH_SHORT).show();
            Log.d("banner", "服务器没有资源,或者解析失败");
        }


    }

}
