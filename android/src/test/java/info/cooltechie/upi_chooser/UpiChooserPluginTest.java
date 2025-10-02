package info.cooltechie.upi_chooser;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import org.junit.Test;

public class UpiChooserPluginTest {
  @Test
  public void onMethodCall_unknownMethod_returnsNotImplemented() {
    UpiChooserPlugin plugin = new UpiChooserPlugin();

    final MethodCall call = new MethodCall("unknown", null);
    MethodChannel.Result mockResult = mock(MethodChannel.Result.class);
    plugin.onMethodCall(call, mockResult);

    verify(mockResult).notImplemented();
  }
}
