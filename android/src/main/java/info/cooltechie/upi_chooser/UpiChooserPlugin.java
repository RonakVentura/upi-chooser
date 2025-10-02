package info.cooltechie.upi_chooser;

import android.util.Log;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.app.Activity;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

/**
 * UpiChooserPlugin (v2 embedding)
 */
public class UpiChooserPlugin implements
        FlutterPlugin,
        MethodChannel.MethodCallHandler,
        ActivityAware,
        PluginRegistry.ActivityResultListener {

    private MethodChannel channel;
    private Context mContext;
    private Activity activity;

    private static final String TAG = "UPI CHOOSER";
    private static final int UNIQUE_REQUEST_CODE = 512078;

    private MethodChannel.Result pendingResult;
    private boolean resultReturned;

    private static final String GOOGLE_PAY_PACKAGE_NAME = "com.google.android.apps.nbu.paisa.user";
    private static final int GOOGLE_PAY_REQUEST_CODE = 123;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        mContext = flutterPluginBinding.getApplicationContext();
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "upi_chooser");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        if ("getUpiApps".equals(call.method)) {
            Log.i(TAG, "args: " + String.valueOf(call.arguments));
            final String payeeAddress = call.argument("payeeAddress");
            final String payeeName    = call.argument("payeeName");
            final String payeeMCC     = call.argument("payeeMCC"); // currently unused
            final String txnID        = call.argument("txnID");    // currently unused
            final String txnRefId     = call.argument("txnRefId");
            final String txnNote      = call.argument("txnNote");  // currently unused
            final String payeeAmount  = call.argument("payeeAmount");
            final String currencyCode = call.argument("currencyCode");
            final String refUrl       = call.argument("refUrl");   // currently unused
            final String mode         = call.argument("mode");     // default to "04" if null
            final String orgid        = call.argument("orgid");
            final String mid          = call.argument("mid");
            final String type         = call.argument("launchType");
            final String pkg          = call.argument("pkg");

            final String uriVal = getUPIString(
                    safe(payeeAddress),
                    safe(payeeName),
                    safe(payeeMCC),
                    safe(txnID),
                    safe(txnRefId),
                    safe(txnNote),
                    safe(payeeAmount),
                    safe(currencyCode),
                    safe(refUrl),
                    safe(mode),
                    safe(orgid),
                    safe(mid)
            );

            Log.i(TAG, "UPI URI: " + uriVal);
            Log.i(TAG, "launchType: " + type);

            if ("chooser".equals(type)) {
                // fire-and-forget; no activity result expected
                openChooser(uriVal);
                result.success("launched_chooser");
                return;
            } else if ("intent".equals(type)) {
                if (activity == null) {
                    result.error("NO_ACTIVITY", "Activity is null; cannot start intent", null);
                    return;
                }
                if (isNullOrEmpty(pkg)) {
                    result.error("NO_PACKAGE", "Package name is required for launchType=intent", null);
                    return;
                }
                // set pending result so onActivityResult can respond
                pendingResult = result;
                resultReturned = false;
                startNewActivity(activity, pkg, uriVal);
                // DO NOT call success here; we'll answer in onActivityResult
                return;
            } else {
                // default behavior: just open chooser
                openChooser(uriVal);
                result.success("launched_default_chooser");
                return;
            }
        } else if ("startGpay".equals(call.method)) {
            if (activity == null) {
                result.error("NO_ACTIVITY", "Activity is null; cannot start GPay", null);
                return;
            }
            pendingResult = result;
            resultReturned = false;
            startGpayActivity();
            return;
        } else {
            result.notImplemented();
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private String getUPIString(
            String payeeAddress,
            String payeeName,
            String payeeMCC,
            String txnID,
            String txnRefId,
            String txnNote,
            String payeeAmount,
            String currencyCode,
            String refUrl,
            String mode,
            String orgid,
            String mid
    ) {
        // base
        String upi = "upi://pay?pa=" + payeeAddress
                + "&pn=" + Uri.encode(payeeName)
                + "&tr=" + Uri.encode(txnRefId)
                + "&am=" + Uri.encode(payeeAmount);

        // currency
        if (isNullOrEmpty(currencyCode)) upi += "&cu=INR";
        else upi += "&cu=" + Uri.encode(currencyCode);

        // mode (default 04)
        upi += "&mode=" + Uri.encode(isNullOrEmpty(mode) ? "04" : mode);

        // optional orgid and mid
        if (!isNullOrEmpty(orgid)) upi += "&orgid=" + Uri.encode(orgid);
        if (!isNullOrEmpty(mid))   upi += "&mid=" + Uri.encode(mid);

        // optionally include note (tn) if provided
        if (!isNullOrEmpty(txnNote)) upi += "&tn=" + Uri.encode(txnNote);

        return upi;
    }

    private void openChooser(String uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        Intent chooser = Intent.createChooser(intent, "Pay with...");
        chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(chooser);
    }

    public void startNewActivity(Context context, String packageName, String uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(uri));
        intent.setPackage(packageName);
        activity.startActivityForResult(intent, UNIQUE_REQUEST_CODE);
    }

    private final Uri gpayUri = new Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", "LC2307029174.lyra@rbl")
            .appendQueryParameter("pn", "name")
            .appendQueryParameter("mc", "52455")
            .appendQueryParameter("tr", "252625eyty765756y")
            .appendQueryParameter("tn", "note")
            .appendQueryParameter("am", "1.00")
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("orgid", "000000")
            .appendQueryParameter("mid", "LC2307029174")
            .appendQueryParameter("mode", "04")
            .build();

    public void startGpayActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(gpayUri);
        intent.setPackage(GOOGLE_PAY_PACKAGE_NAME);
        activity.startActivityForResult(intent, GOOGLE_PAY_REQUEST_CODE);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (channel != null) channel.setMethodCallHandler(null);
        channel = null;
        mContext = null;
    }

    // ---- ActivityAware ----

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        Log.d(TAG, "Attaching to Activity");
        activity = binding.getActivity();
        binding.addActivityResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        Log.d(TAG, "Detaching from Activity for config changes");
        activity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        Log.d(TAG, "Reattaching to Activity for config changes");
        activity = binding.getActivity();
    }

    @Override
    public void onDetachedFromActivity() {
        Log.d(TAG, "Detached from Activity");
        activity = null;
    }

    // ---- Activity Result ----

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        boolean handled = false;

        if (requestCode == UNIQUE_REQUEST_CODE) {
            handled = true;
            if (pendingResult == null) {
                Log.w(TAG, "No pendingResult set for UNIQUE_REQUEST_CODE");
                return true;
            }
            try {
                if (data != null) {
                    String response = data.getStringExtra("response");
                    Log.d(TAG, "RAW RESPONSE FROM REQUESTED APP: " + response);
                    if (!resultReturned) {
                        pendingResult.success(response);
                        resultReturned = true;
                    }
                } else {
                    Log.d(TAG, "Received NULL, User cancelled the transaction.");
                    if (!resultReturned) {
                        pendingResult.error("user_canceled", "User canceled the transaction", null);
                        resultReturned = true;
                    }
                }
            } catch (Exception ex) {
                if (!resultReturned) {
                    pendingResult.error("null_response", "No response received from app", null);
                    resultReturned = true;
                }
            } finally {
                pendingResult = null;
            }
        } else if (requestCode == GOOGLE_PAY_REQUEST_CODE) {
            handled = true;
            if (pendingResult == null) {
                Log.w(TAG, "No pendingResult set for GOOGLE_PAY_REQUEST_CODE");
                return true;
            }
            try {
                if (data != null) {
                    String response = data.getStringExtra("response");
                    if (!resultReturned) {
                        pendingResult.success(response);
                        resultReturned = true;
                    }
                } else {
                    if (!resultReturned) {
                        pendingResult.error("user_canceled", "User canceled the transaction", null);
                        resultReturned = true;
                    }
                }
            } catch (Exception ex) {
                if (!resultReturned) {
                    pendingResult.error("null_response", "No response received from app", null);
                    resultReturned = true;
                }
            } finally {
                pendingResult = null;
            }
        }

        return handled;
    }
}
