package com.app.nisisiafrica;
import android.content.*;import android.graphics.Typeface;import android.os.Bundle;import android.text.TextUtils;import android.view.View;import android.widget.*;
import androidx.annotation.Nullable;import androidx.appcompat.app.AppCompatActivity;
import com.app.nisisiafrica.data.Model.LmsModels;import com.app.nisisiafrica.data.remote.ApiClient;import com.google.android.material.button.MaterialButton;import com.google.firebase.auth.*;
import java.util.*;import io.github.rosemoe.sora.widget.CodeEditor;import retrofit2.*;
public class CourseIdeActivity extends AppCompatActivity{
 public static final String EXTRA_TRACK_ID="extra_track_id";private static final String PREFS="course_ide_drafts";
 private final Map<String,String>s=new LinkedHashMap<>();private String trackId,lang="python";private CodeEditor editor;private EditText stdin;private TextView out;private ProgressBar busy;private MaterialButton run;
 @Override protected void onCreate(@Nullable Bundle b){super.onCreate(b);setContentView(R.layout.activity_course_ide);trackId=getIntent().getStringExtra(EXTRA_TRACK_ID);if(TextUtils.isEmpty(trackId)){finish();return;}starters();
  editor=findViewById(R.id.ideEditor);stdin=findViewById(R.id.ideStdin);out=findViewById(R.id.ideOutput);busy=findViewById(R.id.ideRunProgress);run=findViewById(R.id.ideRun);Spinner spinner=findViewById(R.id.ideLanguage);MaterialButton reset=findViewById(R.id.ideReset);
  editor.setTypefaceText(Typeface.MONOSPACE);editor.setTextSize(14f);String[] langs=s.keySet().toArray(new String[0]);spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,langs));
  spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){String n=langs[pos];if(n.equals(lang))return;save();lang=n;load();clear();}public void onNothingSelected(android.widget.AdapterView<?>p){}});
  load();loadCourseConfig();run.setOnClickListener(v->execute());reset.setOnClickListener(v->{editor.setText(s.get(lang));stdin.setText("");clear();save();});
 }
 private void starters(){s.put("python","print('hello')\n");s.put("javascript","console.log('hello');\n");s.put("typescript","console.log('hello');\n");s.put("java","public class Main {\n  public static void main(String[] args) {\n    System.out.println(\"hello\");\n  }\n}\n");s.put("c","#include <stdio.h>\nint main() {\n  printf(\"hello\\n\");\n  return 0;\n}\n");s.put("cpp","#include <iostream>\nint main() {\n  std::cout << \"hello\\n\";\n  return 0;\n}\n");s.put("html","<h1>hello</h1>\n");}
 private void loadCourseConfig(){FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();if(u==null)return;
  u.getIdToken(false).addOnSuccessListener(t->ApiClient.getLmsService().trackIdeConfig("Bearer "+t.getToken(),trackId).enqueue(new Callback<LmsModels.IdeConfigEnvelope>(){
   public void onResponse(Call<LmsModels.IdeConfigEnvelope>c,Response<LmsModels.IdeConfigEnvelope>r){LmsModels.IdeConfigEnvelope b=r.body();if(!r.isSuccessful()||b==null||!b.ok||b.data==null)return;LmsModels.IdeConfigDto x=b.data;String l=x.language==null?"python":x.language;if(x.starter!=null)s.put(l,x.starter);SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);if(!p.contains(trackId+":"+l)&&l.equals(lang)){editor.setText(s.get(l));if(x.stdin!=null)stdin.setText(x.stdin);save();}}
   public void onFailure(Call<LmsModels.IdeConfigEnvelope>c,Throwable t){}
  }));}
 private void execute(){FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();if(u==null){show("Sign in to run code.");return;}String src=editor.getText().toString();if(src.trim().isEmpty()){show("Write some code first.");return;}save();running(true);
  u.getIdToken(false).addOnSuccessListener(t->ApiClient.getLmsService().runTrackIde("Bearer "+t.getToken(),trackId,new LmsModels.IdeRunBody(lang,src,stdin.getText().toString())).enqueue(new Callback<LmsModels.IdeRunEnvelope>(){
   public void onResponse(Call<LmsModels.IdeRunEnvelope>c,Response<LmsModels.IdeRunEnvelope>r){running(false);LmsModels.IdeRunEnvelope b=r.body();if(!r.isSuccessful()||b==null||!b.ok||b.data==null){show(b!=null&&b.error!=null?b.error:"Could not run code ("+r.code()+")");return;}LmsModels.IdeRunResult x=b.data;if("html".equals(lang)&&x.html!=null){show("HTML ran successfully. Native preview is coming next.\n\n"+x.html);return;}String text=(x.stderr==null?"":x.stderr)+(x.stdout==null?"":x.stdout);show(text.isEmpty()?(x.passed?"Code ran successfully.":"No output."):text);}
   public void onFailure(Call<LmsModels.IdeRunEnvelope>c,Throwable t){running(false);show("Run failed. Check your connection and try again.");}
  })).addOnFailureListener(e->{running(false);show("Could not authenticate the IDE session.");});
 }
 private void running(boolean x){run.setEnabled(!x);busy.setVisibility(x?View.VISIBLE:View.GONE);run.setText(x?"Running…":"Run");}private void show(String x){out.setText(x);out.setVisibility(View.VISIBLE);}private void clear(){out.setText("");out.setVisibility(View.GONE);}
 private String key(){return trackId+":"+lang;}private void load(){SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);editor.setText(p.getString(key(),s.get(lang)));stdin.setText(p.getString(key()+":stdin",""));}
 private void save(){if(editor==null)return;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(key(),editor.getText().toString()).putString(key()+":stdin",stdin.getText().toString()).apply();}
 @Override protected void onPause(){save();super.onPause();}@Override protected void onDestroy(){if(editor!=null)editor.release();super.onDestroy();}
}