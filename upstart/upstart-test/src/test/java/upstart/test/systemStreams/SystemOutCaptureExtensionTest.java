package upstart.test.systemStreams;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import upstart.test.SetupPhase;

import static com.google.common.truth.Truth.assertWithMessage;

@CaptureSystemOut
class SystemOutCaptureExtensionTest {
  private final SystemOutCaptor captor;

  SystemOutCaptureExtensionTest(SystemOutCaptor captor) {
    this.captor = captor;
  }

  @Test
  void captureAutoStarts() {
    assertIsCapturing();
    String output = "<captured output>";
    System.out.print(output);
    assertWithMessage("captured output").that(captor.getCapturedString()).isEqualTo(output);
  }

  @CaptureSystemOut(SetupPhase.None)
  @Test
  void autoStartDisabledByMethodAnnotation() {
    assertNotCapturing();
  }

  @CaptureSystemOut(SetupPhase.None)
  @Nested
  class InnerTest {
    @Test
    void autoStartDisabledByClassAnnotation() {
      assertNotCapturing();
    }

    @CaptureSystemOut
    @Test
    void autoStartReenabledByMethodAnnotation() {
      assertIsCapturing();
    }
  }

  private void assertIsCapturing() {
    assertWithMessage("captor autostarted").that(captor.isCapturing()).isTrue();
  }

  private void assertNotCapturing() {
    assertWithMessage("captor autostarted").that(captor.isCapturing()).isFalse();
  }
}