package com.qs.mysoundrecorder;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class RecordingFileList extends Activity implements ImageButton.OnClickListener {
    private static final String TAG = "SR/RecordingFileList";
    private static final String DIALOG_TAG_DELETE = "Delete";
    private static final String DIALOG_TAG_PROGRESS = "Progress";
    private static final String RECORDING_FILELIST_DATA = "recording_filelist_data";
    private static final String REMOVE_PROGRESS_DIALOG_KEY = "remove_progress_dialog";
    public static final int NORMAL = 1;
    public static final int EDIT = 2;

    public static final String PATH = "path";
    public static final String DURATION = "duration";
    public static final String FILE_NAME = "filename";
    public static final String CREAT_DATE = "creatdate";
    public static final String FORMAT_DURATION = "formatduration";
    public static final String RECORD_ID = "recordid";
    private static final int PATH_INDEX = 2;
    private static final int DURATION_INDEX = 3;
    private static final int CREAT_DATE_INDEX = 5;
    private static final int RECORD_ID_INDEX = 7;
    private static final int ONE_SECOND = 1000;
    private static final int TIME_BASE = 60;
    private static final int NO_CHECK_POSITION = -1;
    private static final int DEFAULT_SLECTION = -1;
    private int mCurrentAdapterMode = NORMAL;
    private int mSelection = 0;
    private int mTop = 0;
    private boolean mNeedRemoveProgressDialog = false;

    private final ArrayList<HashMap<String, Object>> mArrlist = new ArrayList<HashMap<String, Object>>();
    private final ArrayList<String> mNameList = new ArrayList<String>();
    private final ArrayList<String> mPathList = new ArrayList<String>();
    private final ArrayList<String> mTitleList = new ArrayList<String>();
    private final ArrayList<String> mDurationList = new ArrayList<String>();
    private final List<Integer> mIdList = new ArrayList<Integer>();
    private List<Integer> mCheckedList = new ArrayList<Integer>();

    private BroadcastReceiver mSDCardMountEventReceiver = null;
    private boolean mActivityForeground = true;

    private ListView mRecordingFileListView;
    private ImageButton mRecordButton;
    private ImageButton mDeleteButton;
    private View mEmptyView;
    private QueryDataTask mQueryTask;
    private Handler mHandler;

	private TextView mTitleView;
	private LinearLayout mBottomLinearLayout;
	private static final int REQURST_DELETE_FILE = 1001;
	private TextView mCancel;
	private TextView mOK;
	private Dialog mDialog;
	
    private final DialogInterface.OnClickListener mDeleteDialogListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface arg0, int arg1) {
            Log.d(TAG, "<mDeleteDialogListener onClick>");
            deleteItems();
            arg0.dismiss();
        }
    };

    @Override
    public void onCreate(Bundle icycle) {
        super.onCreate(icycle);
        Log.d(TAG, "<onCreate> begin");

	requestWindowFeature(Window.FEATURE_NO_TITLE);
	requestWindowFeature(Window.FEATURE_SWIPE_TO_DISMISS);
		
        setContentView(R.layout.recording_file_list);
		mTitleView = (TextView) findViewById(R.id.title_view);
		mBottomLinearLayout = (LinearLayout)findViewById(R.id.bottomLinearLayout);
		mTitleView.setText(R.string.recording_file_list);
        mRecordingFileListView = (ListView) findViewById(R.id.recording_file_list_view);
        mRecordButton = (ImageButton) findViewById(R.id.recordButton);
        mDeleteButton = (ImageButton) findViewById(R.id.deleteButton);
        mEmptyView = findViewById(R.id.empty_view);
        mRecordButton.setOnClickListener(this);
        mDeleteButton.setOnClickListener(this);
        mRecordingFileListView.setOnCreateContextMenuListener(this);
        mHandler = new Handler();
        mRecordingFileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long itemId) {
                if (EDIT == mCurrentAdapterMode) {
                    int id = (int) ((EditViewAdapter) mRecordingFileListView.getAdapter())
                            .getItemId(position);
                    CheckBox checkBox = (CheckBox) view.findViewById(R.id.record_file_checkbox);
                    if (checkBox.isChecked()) {
                        checkBox.setChecked(false);
                        ((EditViewAdapter) mRecordingFileListView.getAdapter()).setCheckBox(id,
                                false);
                        int count = ((EditViewAdapter) mRecordingFileListView.getAdapter())
                                .getCheckedItemsCount();
                        if (0 == count) {
                            saveLastSelection();
                            mCurrentAdapterMode = NORMAL;
                            swicthAdapterView(NO_CHECK_POSITION);
                        }
                    } else {
                        checkBox.setChecked(true);
                        ((EditViewAdapter) mRecordingFileListView.getAdapter()).setCheckBox(id,
                                true);
                    }
                } else {
                    Intent intent = new Intent();
                    HashMap<String, Object> map = (HashMap<String, Object>) mRecordingFileListView
                            .getItemAtPosition(position);
                    intent.putExtra(SoundRecorder.DOWHAT, SoundRecorder.PLAY);
                    if (null != map) {
                        if (null != map.get(PATH)) {
                            intent.putExtra(PATH, map.get(PATH).toString());
                        }
                        if (null != map.get(FILE_NAME)) {
                            intent.putExtra(FILE_NAME, map.get(FILE_NAME).toString());
                        }
                        if (null != map.get(DURATION)) {
                            intent.putExtra(DURATION, Integer
                                    .parseInt(map.get(DURATION).toString()));
                        }
                    }
                    intent.setClass(RecordingFileList.this, MediaPlayerActivity.class);
                    startActivity(intent);
                }
            }
        });

        mRecordingFileListView
                .setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
                            long itemId) {
                        int id = 0;
                        if (EDIT == mCurrentAdapterMode) {
                            id = (int) ((EditViewAdapter) mRecordingFileListView.getAdapter())
                                    .getItemId(position);
                        } else {
                            HashMap<String, Object> map = (HashMap<String, Object>) mRecordingFileListView
                                    .getItemAtPosition(position);
                            id = (Integer) map.get(RECORD_ID);
                        }
                        if (mCurrentAdapterMode == NORMAL) {
                            saveLastSelection();
                            mCurrentAdapterMode = EDIT;
                            swicthAdapterView(id);
                        }
                        return true;
                    }
                });
        registerExternalStorageListener();

        enableDialogButtons(false);
        ListViewProperty listViewProperty = (ListViewProperty) getLastNonConfigurationInstance();
        if (null != listViewProperty) {
            if (null != listViewProperty.getCheckedList()) {
                mCheckedList = listViewProperty.getCheckedList();
            }
            mSelection = listViewProperty.getCurPos();
            mTop = listViewProperty.getTop();
        }
        Log.d(TAG, "<onStart> end");
        setListData(mCheckedList);

        Log.d(TAG, "<onCreate> end");
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        Log.d(TAG, "<onRetainNonConfigurationInstance> begin");
        List<Integer> checkedList = null;
        saveLastSelection();
        if (EDIT == mCurrentAdapterMode) {
            if (null != ((EditViewAdapter) mRecordingFileListView.getAdapter())) {
                checkedList = ((EditViewAdapter) mRecordingFileListView.getAdapter())
                        .getCheckedPosList();
                Log.d(TAG, "<onRetainNonConfigurationInstance> checkedList.size() = "
                        + checkedList.size());
            }
        }
        ListViewProperty listViewProperty = new ListViewProperty(checkedList, mSelection, mTop);

        SharedPreferences prefs = getSharedPreferences(RECORDING_FILELIST_DATA, 0);
        SharedPreferences.Editor ed = prefs.edit();
        Log.d(TAG, "<onRetainNonConfigurationInstance> mNeedRemoveProgressDialog = "
                + mNeedRemoveProgressDialog);
        ed.putBoolean(REMOVE_PROGRESS_DIALOG_KEY, mNeedRemoveProgressDialog);
        ed.commit();
        Log.d(TAG, "<onRetainNonConfigurationInstance> end");
        return listViewProperty;
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(TAG, "<onRestoreInstanceState> begin");
        Fragment fragment = getFragmentManager().findFragmentByTag(DIALOG_TAG_DELETE);
        Log.d(TAG, "<onRestoreInstanceState> getFragmentManager() = " + getFragmentManager());
        if (null != fragment) {
            ((DeleteDialogFragment) fragment).setOnClickListener(mDeleteDialogListener);
            Log.d(TAG, "<onRestoreInstanceState> getFragmentManager() = "
                    + getFragmentManager());
        }
        SharedPreferences prefs = getSharedPreferences(RECORDING_FILELIST_DATA, 0);
        if (prefs.getBoolean(REMOVE_PROGRESS_DIALOG_KEY, false)) {
            removeOldFragmentByTag(DIALOG_TAG_PROGRESS);
        }
        mNeedRemoveProgressDialog = false;
        Log.d(TAG, "<onRestoreInstanceState> end");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "<onResume> begin");

        mActivityForeground = true;
        Log.d(TAG, "<onResume> end");
    }

    /**
     * This method save the selection of list view on present screen
     */
    protected void saveLastSelection() {
        Log.d(TAG, "<saveLastSelection>");
        if (null != mRecordingFileListView) {
            mSelection = mRecordingFileListView.getFirstVisiblePosition();
            View cv = mRecordingFileListView.getChildAt(0);
            if (null != cv) {
                mTop = cv.getTop();
            }
        }
    }

    /**
     * This method restore the selection saved before
     */
    protected void restoreLastSelection() {
        Log.d(TAG, "<restoreLastSelection>");
        if (mSelection >= 0) {
            mRecordingFileListView.setSelectionFromTop(mSelection, mTop);
            mSelection = DEFAULT_SLECTION;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "<onStart> begin");

    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "<onStop> begin");
    }
    /**
     * bind data to list view
     *
     * @param list
     *            the index list of current checked items
     */
    private void setListData(List<Integer> list) {
        Log.d(TAG, "<setListData>");
        mRecordingFileListView.setAdapter(null);
        if (mQueryTask != null) {
            mQueryTask.cancel(false);
        }
        mQueryTask = new QueryDataTask(list);
        mQueryTask.execute();
    }

    /**
     * query sound recorder recording file data
     *
     * @return the query list of the map from String to Object
     */
    public ArrayList<HashMap<String, Object>> queryData() {
        Log.d(TAG, "<queryData>");
        mArrlist.clear();
        mNameList.clear();
        mPathList.clear();
        mTitleList.clear();
        mDurationList.clear();
        mIdList.clear();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("is_record");
        stringBuilder.append(" =1");
        String selection = stringBuilder.toString();
        Cursor recordingFileCursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[] {
                        MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED,
                        MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media._ID
                }, selection, null, null);
        try {
            if ((null == recordingFileCursor) || (0 == recordingFileCursor.getCount())) {
                Log.d(TAG, "<queryData> the data return by query is null");
                return null;
            }
            Log.d(TAG, "<queryData> the data return by query is available");
            recordingFileCursor.moveToFirst();
            int num = recordingFileCursor.getCount();
            final int sizeOfHashMap = 6;
            String path = null;
            String fileName = null;
            int duration = 0;
            long cDate = 0;
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getResources().getString(
                        R.string.audio_db_title_format));
            String createDate = null;
            int recordId = 0;
            Date date = new Date();
            for (int j = 0; j < num; j++) {
                HashMap<String, Object> map = new HashMap<String, Object>(sizeOfHashMap);
                path = recordingFileCursor.getString(PATH_INDEX);
                if (null != path) {
                    fileName = path.substring(path.lastIndexOf("/") + 1, path.length());
                }
                duration = recordingFileCursor.getInt(DURATION_INDEX);
                if (duration < ONE_SECOND) {
                    duration = ONE_SECOND;
                }
                cDate = recordingFileCursor.getInt(CREAT_DATE_INDEX);
                date.setTime(cDate * ONE_SECOND);
                createDate = simpleDateFormat.format(date);
                recordId = recordingFileCursor.getInt(RECORD_ID_INDEX);

                map.put(FILE_NAME, fileName);
                map.put(PATH, path);
                map.put(DURATION, duration);
                map.put(CREAT_DATE, createDate);
                map.put(FORMAT_DURATION, formatDuration(duration));
                map.put(RECORD_ID, recordId);

                mNameList.add(fileName);
                mPathList.add(path);
                mTitleList.add(createDate);
                mDurationList.add(formatDuration(duration));
                mIdList.add(recordId);

                recordingFileCursor.moveToNext();
                mArrlist.add(map);
            }
        } catch (IllegalStateException e) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    ErrorHandle.showErrorInfo(RecordingFileList.this, ErrorHandle.ERROR_ACCESSING_DB_FAILED_WHEN_QUERY);
                }
            });
            e.printStackTrace();
        } finally {
            if (null != recordingFileCursor) {
                recordingFileCursor.close();
            }
        }
        Collections.reverse(mArrlist);
        Collections.reverse(mNameList);
        Collections.reverse(mPathList);
        Collections.reverse(mTitleList);
        Collections.reverse(mDurationList);
        Collections.reverse(mIdList);
        return mArrlist;
    }

    /**
     * update UI after query data
     *
     * @param list
     *            the list of query result
     */
    public void afterQuery(List<Integer> list) {
        Log.d(TAG, "<afterQuery>");
        if (null == list) {
            mCurrentAdapterMode = NORMAL;
            swicthAdapterView(NO_CHECK_POSITION);
        } else {
            list.retainAll(mIdList);
            if (list.isEmpty()) {
                removeOldFragmentByTag(DIALOG_TAG_DELETE);
                mCurrentAdapterMode = NORMAL;
                swicthAdapterView(NO_CHECK_POSITION);
            } else {
                // for refresh status of DeleteDialogFragment(single/multi)
                Log.d(TAG, "<afterQuery> list.size() = " + list.size());
                enableDialogButtons(true);
                setDeleteDialogSingle(1 == list.size());
                mCurrentAdapterMode = EDIT;
                EditViewAdapter adapter = new EditViewAdapter(getApplicationContext(), mNameList,
                        mPathList, mTitleList, mDurationList, mIdList, list);
                mRecordingFileListView.setAdapter(adapter);
                mDeleteButton.setVisibility(View.VISIBLE);
                mRecordButton.setVisibility(View.GONE);
                restoreLastSelection();
            }
        }
    }

    /**
     * format duration to display as 00:00
     *
     * @param duration
     *            the duration to be format
     * @return the String after format
     */
    private String formatDuration(int duration) {
        String timerFormat = getResources().getString(R.string.timer_format);
        int time = duration / ONE_SECOND;
        return String.format(timerFormat, time / TIME_BASE, time % TIME_BASE);
    }

    @Override
    public void onClick(View button) {
        switch (button.getId()) {
        case R.id.recordButton:
            Log.d(TAG, "<onClick> recordButton");
            mRecordButton.setEnabled(false);
            Intent intent = new Intent();
            intent.setClass(this, SoundRecorder.class);
            intent.putExtra(SoundRecorder.DOWHAT, SoundRecorder.RECORD);
            setResult(RESULT_OK, intent);
            finish();
            break;
        case R.id.deleteButton:
            Log.d(TAG, "<onClick> deleteButton");
            EditViewAdapter adapter = (EditViewAdapter)  mRecordingFileListView.getAdapter();
            if (adapter != null) {
                showDeleteDialog(adapter.getCheckedItemsCount() == 1);
				//showDeleteFileDialog();
            }
            break;
		case R.id.ok:
			deleteItems();
			dissmissDialog();
			switchRecordingFileList();
			break;
		case R.id.cancel:
			dissmissDialog();
			break;
        default:
            break;
        }
    }

    /**
     * show DeleteDialogFragment
     *
     * @param single
     *            if the number of files to be deleted == 0 ?
     */
    private void showDeleteDialog(boolean single) {
        Log.d(TAG, "<showDeleteDialog> single = " + single);
        removeOldFragmentByTag(DIALOG_TAG_DELETE);
        FragmentManager fragmentManager = getFragmentManager();
        Log.d(TAG, "<showDeleteDialog> fragmentManager = " + fragmentManager);
        DialogFragment newFragment = DeleteDialogFragment.newInstance(single);
        ((DeleteDialogFragment) newFragment).setOnClickListener(mDeleteDialogListener);
        newFragment.show(fragmentManager, DIALOG_TAG_DELETE);
        fragmentManager.executePendingTransactions();
    }

    /**
     * remove old DialogFragment
     *
     * @param tag
     *            the tag of DialogFragment to be removed
     */
    private void removeOldFragmentByTag(String tag) {
        Log.d(TAG, "<removeOldFragmentByTag> tag = " + tag);
        FragmentManager fragmentManager = getFragmentManager();
        Log.d(TAG, "<removeOldFragmentByTag> fragmentManager = " + fragmentManager);
        DialogFragment oldFragment = (DialogFragment) fragmentManager.findFragmentByTag(tag);
        Log.d(TAG, "<removeOldFragmentByTag> oldFragment = " + oldFragment);
        if (null != oldFragment) {
            oldFragment.dismissAllowingStateLoss();
        }
    }

    /**
     * update the message of delete dialog
     *
     * @param single
     *            if single file to be deleted
     */
    private void setDeleteDialogSingle(boolean single) {
        FragmentManager fragmentManager = getFragmentManager();
        Log.d(TAG, "<setDeleteDialogSingle> fragmentManager = " + fragmentManager);
        DeleteDialogFragment oldFragment = (DeleteDialogFragment) fragmentManager
                .findFragmentByTag(DIALOG_TAG_DELETE);
        if (null == oldFragment) {
            Log.d(TAG, "<setDeleteDialogSingle> no old delete dialog");
        } else {
            oldFragment.setSingle(single);
            Log.d(TAG, "<setDeleteDialogSingle> setSingle single = " + single);
        }
    }

    /**
     * call delete file task
     */
    public void deleteItems() {
        Log.d(TAG, "<deleteItems> call FileTask to delete");
        FileTask fileTask = new FileTask();
        fileTask.execute();
    }

    /**
     * switch adapter mode between NORMAL and EDIT
     *
     * @param pos
     *            the index of current clicked item
     */
    public void swicthAdapterView(int pos) {
        if (NORMAL == mCurrentAdapterMode) {
            Log.d(TAG, "<swicthAdapterView> from edit mode to normal mode");
            SimpleAdapter adapter = new SimpleAdapter(getApplicationContext(), mArrlist,
                    R.layout.navigation_adapter, new String[] { FILE_NAME, CREAT_DATE,
                            FORMAT_DURATION }, new int[] { R.id.record_file_name,
                            R.id.record_file_title, R.id.record_file_duration });
            mRecordingFileListView.setAdapter(adapter);
            mDeleteButton.setVisibility(View.GONE);
            mRecordButton.setVisibility(View.VISIBLE);
			mBottomLinearLayout.setVisibility(View.GONE);
			mTitleView.setText(R.string.recording_file_list);
			mRecordingFileListView.setPadding(mRecordingFileListView.getPaddingLeft(), mRecordingFileListView.getPaddingTop(),
				mRecordingFileListView.getPaddingRight(), 60);
        } else {
            Log.d(TAG, "<swicthAdapterView> from normal mode to edit mode");
            EditViewAdapter adapter = new EditViewAdapter(getApplicationContext(), mNameList,
                    mPathList, mTitleList, mDurationList, mIdList, pos);
            mRecordingFileListView.setAdapter(adapter);
            mDeleteButton.setVisibility(View.VISIBLE);
            mRecordButton.setVisibility(View.GONE);
			mBottomLinearLayout.setVisibility(View.VISIBLE);
			mTitleView.setText(R.string.delete_files);
			mRecordingFileListView.setPadding(mRecordingFileListView.getPaddingLeft(), mRecordingFileListView.getPaddingTop(),
				mRecordingFileListView.getPaddingRight(), 0);
        }
        restoreLastSelection();
    }

    /**
     * The method gets the selected items and create a list of File objects
     *
     * @return a list of File objects
     */
    protected List<File> getSelectedFiles() {
        Log.d(TAG, "<getSelectedFiles> begin");
        List<File> list = new ArrayList<File>();
        if (EDIT != mCurrentAdapterMode) {
            Log.d(TAG, "<getSelectedFiles> end");
            return list;
        }
        if (null != ((EditViewAdapter) mRecordingFileListView.getAdapter())) {
            List<String> checkedList = ((EditViewAdapter) mRecordingFileListView
                    .getAdapter()).getCheckedItemsList();
            int listSize = checkedList.size();
            for (int i = 0; i < listSize; i++) {
                File file = new File(checkedList.get(i));
                list.add(file);
            }
        }
        Log.d(TAG, "<getSelectedFiles> end");
        return list;
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "<onPause> begin");
        List<Integer> checkedList = null;
        if (mQueryTask != null) {
            // should cancel async task to avoid the last task post
            // finished message to main handler when the new async task is executing.
            mQueryTask.cancel(false);
            mQueryTask = null;
        }
        if (EDIT == mCurrentAdapterMode) {
            if (null != ((EditViewAdapter) mRecordingFileListView.getAdapter())) {
                checkedList = ((EditViewAdapter) mRecordingFileListView.getAdapter())
                        .getCheckedPosList();
                if (!checkedList.isEmpty()) {
                    mCheckedList = checkedList;
                    Log.d(TAG, "<onPause> save checkedList; mCheckedList.size() = " + mCheckedList.size());
                }
            }
        } else {
            mCheckedList = null;
        }
        mActivityForeground = false;
        saveLastSelection();
        Log.d(TAG, "<onPause> end");
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed");
        if (EDIT == mCurrentAdapterMode) {
            mCurrentAdapterMode = NORMAL;
            saveLastSelection();
            swicthAdapterView(NO_CHECK_POSITION);
        } else {
            finishSelf();
            super.onBackPressed();
        }

		if(View.VISIBLE == mBottomLinearLayout.getVisibility()){
				mBottomLinearLayout.setVisibility(View.GONE);
		}
    }
	
	//add start

	private void showDeleteFileDialog() {
		mDialog = new Dialog(this,R.style.DialogFullscreen);
		View contentView = LayoutInflater.from(this).inflate(R.layout.delete_dialog, null);
		mDialog.setContentView(contentView);
		
		mCancel = (TextView) contentView.findViewById(R.id.cancel);
		mOK = (TextView) contentView.findViewById(R.id.ok);
		mCancel.setOnClickListener(this);
		mOK.setOnClickListener(this);
		
		Window window = mDialog.getWindow();  
		window.setWindowAnimations(R.style.DialogAnim);  
		if(mDialog!= null&&!mDialog.isShowing()){
			mDialog.show();
		}
	}

	private void dissmissDialog(){
		if(mDialog!=null&&mDialog.isShowing()){
			mDialog.dismiss();
		}
	}

	private void switchRecordingFileList(){
		if(View.VISIBLE == mBottomLinearLayout.getVisibility()){
				mBottomLinearLayout.setVisibility(View.GONE);
		}
	}
	
	//add end
	
    /**
     * FileTask for delete some recording file
     */
    public class FileTask extends AsyncTask<Void, Object, Boolean> {
        /**
         * A callback method to be invoked before the background thread starts
         * running
         */
        @Override
        protected void onPreExecute() {
            Log.d(TAG, "<FileTask.onPreExecute>");
            if (mActivityForeground) {
                Log.d(TAG, "<FileTask.onPreExecute> Activity is running in foreground");
                FragmentManager fragmentManager = getFragmentManager();
                Log.d(TAG, "<FileTask.onPreExecute> fragmentManager = " + fragmentManager);
                DialogFragment newFragment = ProgressDialogFragment.newInstance();
                newFragment.show(fragmentManager, DIALOG_TAG_PROGRESS);
                fragmentManager.executePendingTransactions();
            } else {
                Log.d(TAG, "<FileTask.onPreExecute> Activity is running in background");
            }
        }

        /**
         * A callback method to be invoked when the background thread starts
         * running
         *
         * @param params
         *            the method need not parameters here
         * @return true/false, success or fail
         */
        @Override
        protected Boolean doInBackground(Void... params) {
            Log.d(TAG, "<FileTask.doInBackground> begin");
            // delete files
            List<File> list = getSelectedFiles();
            int listSize = list.size();
            Log.d(TAG, "<FileTask.doInBackground> the number of delete files: " + listSize);
            for (int i = 0; i < listSize; i++) {
                File file = list.get(i);
                if (null != file) {
                    Log.d(TAG,
                            "<doInBackground>, the file to be delete is:" + file.getAbsolutePath());
                }
                if (!file.delete()) {
                    Log.d(TAG, "<FileTask.doInBackground> delete file ["
                            + list.get(i).getAbsolutePath() + "] fail");
                }
                if (!SoundRecorderUtils.deleteFileFromMediaDB(getApplicationContext(),
                        file.getAbsolutePath())) {
                    return false;
                }
            }
            Log.d(TAG, "<FileTask.doInBackground> end");
            return true;
        }

        /**
         * A callback method to be invoked after the background thread performs
         * the task
         *
         * @param result
         *            the value returned by doInBackground()
         */
        @Override
        protected void onPostExecute(Boolean result) {
            Log.d(TAG, "<FileTask.onPostExecute>");
            removeOldFragmentByTag(DIALOG_TAG_PROGRESS);
            mNeedRemoveProgressDialog = true;
            if (mActivityForeground) {
                mCurrentAdapterMode = NORMAL;
                if (!result) {
                    ErrorHandle.showErrorInfo(RecordingFileList.this,
                            ErrorHandle.ERROR_DELETING_FAILED);
                }
                setListData(null);
            }
        }

        /**
         * A callback method to be invoked when the background thread's task is
         * cancelled
         */
        @Override
        protected void onCancelled() {
            Log.d(TAG, "<FileTask.onCancelled>");
            FragmentManager fragmentManager = getFragmentManager();
            Log.d(TAG, "<FileTask.onCancelled> fragmentManager = " + fragmentManager);
            DialogFragment oldFragment = (DialogFragment) fragmentManager
                    .findFragmentByTag(DIALOG_TAG_PROGRESS);
            if (null != oldFragment) {
                oldFragment.dismissAllowingStateLoss();
            }
        }
    }

    /**
     * through AsyncTask to query recording file data from database
     */
    public class QueryDataTask extends AsyncTask<Void, Object, ArrayList<HashMap<String, Object>>> {
        List<Integer> mList;

        /**
         * the construction of QueryDataTask
         *
         * @param list
         *            the index list of current checked items
         */
        QueryDataTask(List<Integer> list) {
            mList = list;
        }

        /**
         * query data from database
         *
         * @param params
         *            no parameter
         * @return the query result
         */
        protected ArrayList<HashMap<String, Object>> doInBackground(Void... params) {
            Log.d(TAG, "<QueryDataTask.doInBackground>");
            return queryData();
        }

        @Override
        protected void onPostExecute(ArrayList<HashMap<String, Object>> result) {
            Log.d(TAG, "<QueryDataTask.onPostExecute>");
            if (mQueryTask == QueryDataTask.this) {
                mQueryTask = null;
            }
            if (QueryDataTask.this.isCancelled()) {
                Log.d(TAG, "<QueryDataTask.onPostExceute> task is cancelled, return.");
                return;
            }
            if (mActivityForeground) {
                if (null == result) {
                    removeOldFragmentByTag(DIALOG_TAG_DELETE);
                    mRecordingFileListView.setEmptyView(mEmptyView);
                   	mBottomLinearLayout.setVisibility(View.GONE);
					mTitleView.setText(R.string.recording_file_list);
                } else {
                    afterQuery(mList);
                }
            }
        }
    }

    /**
     * setResult to SoundRecorder and finish self
     */
    public void finishSelf() {
        Log.d(TAG, "<finishSelf>");
        mCurrentAdapterMode = NORMAL;
        Intent intent = new Intent();
        intent.setClass(this, SoundRecorder.class);
        intent.putExtra(SoundRecorder.DOWHAT, SoundRecorder.INIT);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "<onDestroy> begin");
        if (null != mSDCardMountEventReceiver) {
            unregisterReceiver(mSDCardMountEventReceiver);
            mSDCardMountEventReceiver = null;
        }
        Log.d(TAG, "<onDestroy> end");
        super.onDestroy();
    }

    /**
     * deal with SDCard mount and eject event
     */
    private void registerExternalStorageListener() {
        Log.d(TAG, "<registerExternalStorageListener>");
        if (null == mSDCardMountEventReceiver) {
            mSDCardMountEventReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action != null && (action.equals(Intent.ACTION_MEDIA_EJECT)
                            || action.equals(Intent.ACTION_MEDIA_UNMOUNTED))) {
                        mRecordingFileListView.setAdapter(null);
                        mCheckedList = null;
                        ErrorHandle.showErrorInfo(RecordingFileList.this,
                                ErrorHandle.ERROR_SD_UNMOUNTED_ON_FILE_LIST);
                        finishSelf();
                    } else if (action != null && action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        setListData(mCheckedList);
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mSDCardMountEventReceiver, iFilter);
        }
    }

    private void enableDialogButtons(boolean isEnabled) {
        Fragment fragment = getFragmentManager().findFragmentByTag(DIALOG_TAG_DELETE);
        if (null != fragment) {
            LogUtils.d(TAG, "enableDialogButtons " + isEnabled);
            ((DeleteDialogFragment) fragment).setButton(DialogInterface.BUTTON_POSITIVE, isEnabled);
            ((DeleteDialogFragment) fragment).setButton(DialogInterface.BUTTON_NEGATIVE, isEnabled);
        }
    }
}
