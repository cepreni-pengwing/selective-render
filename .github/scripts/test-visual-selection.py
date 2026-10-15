import importlib.util
import io
from contextlib import redirect_stdout
from pathlib import Path
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location("selection",Path(__file__).with_name("select-visual-tests.py"))
selection=importlib.util.module_from_spec(spec); spec.loader.exec_module(selection)

class VisualSelectionTest(unittest.TestCase):
    def test_menu_changes_select_ui_screenshots(self):
        self.assertTrue(selection.relevant(["versions/mc26.2/src/client/java/de/selectiverender/RegionEditorScreen.java"]))
        self.assertTrue(selection.relevant(["src/client/java/de/selectiverender/BoundaryColorControls.java"]))
    def test_logic_docs_and_version_changes_do_not_select_screenshots(self):
        self.assertFalse(selection.relevant(["README.md","gradle.properties","src/client/java/de/selectiverender/SelectiveRenderState.java"]))
        self.assertFalse(selection.relevant([]))
    def test_explicit_visual_request_overrides_unchanged_menu(self):
        with patch.dict(selection.os.environ,{"VISUAL_REQUESTED":"true","VISUAL_BASE":"abc","VISUAL_HEAD":"def"}), patch.object(selection.subprocess,"check_output",return_value="README.md\n"), redirect_stdout(io.StringIO()) as output:
            selection.main()
        self.assertEqual("visual=true",output.getvalue().strip())
    def test_ordinary_build_keeps_screenshot_tests_off(self):
        with patch.dict(selection.os.environ,{"VISUAL_REQUESTED":"false","VISUAL_BASE":"abc","VISUAL_HEAD":"def"}), patch.object(selection.subprocess,"check_output",return_value="gradle.properties\n"), redirect_stdout(io.StringIO()) as output:
            selection.main()
        self.assertEqual("visual=false",output.getvalue().strip())

if __name__=="__main__": unittest.main()
