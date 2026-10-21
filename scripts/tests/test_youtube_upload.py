import contextlib
import importlib.util
import io
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import Mock, patch

import requests


spec = importlib.util.spec_from_file_location(
    "youtube_upload", Path(__file__).resolve().parents[1] / "youtube_upload.py")
upload = importlib.util.module_from_spec(spec)
spec.loader.exec_module(upload)


def response(code, body=None, headers=None):
    result = Mock(status_code=code, headers=headers or {})
    result.json.return_value = body or {}
    return result


class YouTubeUploadTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.folder = Path(self.temporary.name)
        self.file = self.folder / "clip.mp4"
        self.file.write_bytes(b"12345678")
        self.record = self.folder / "upload.json"
        self.session = Mock()
        self.session.post.return_value = response(200, headers={
            "Location": "https://www.googleapis.com/upload/youtube/v3/videos?upload_id=test"})
        self.output = contextlib.redirect_stdout(io.StringIO())
        self.output.__enter__()
        self.addCleanup(self.output.__exit__, None, None, None)

    def saved_session(self):
        state = {"sha256": upload.fingerprint(self.file), "sizeBytes": 8,
                 "title": "2026-10-21", "channelId": "channel-id", "privacyStatus": "unlisted",
                 "sessionUri": "https://www.googleapis.com/upload/youtube/v3/videos?upload_id=test",
                 "confirmedBytes": 4, "state": "UPLOADING"}
        upload.save_json(self.record, state)
        return state

    def run_upload(self):
        return upload.upload_file(self.session, self.file, "2026-10-21", "channel-id", self.record)

    def test_upload_uses_unlisted_and_sends_confirmed_ranges(self):
        self.session.put.side_effect = [response(308), response(308, headers={"Range": "bytes=0-3"}),
                                        response(201, {"id": "videoABC123"})]
        with patch.object(upload, "CHUNK_SIZE", 4):
            result = self.run_upload()
        self.assertEqual(result["videoId"], "videoABC123")
        self.assertEqual(self.session.post.call_args.kwargs["json"]["status"]["privacyStatus"], "unlisted")
        self.assertEqual(self.session.post.call_args.kwargs["json"]["snippet"]["title"], "2026-10-21")
        self.assertEqual(self.session.post.call_args.kwargs["params"]["notifySubscribers"], "false")
        calls = self.session.put.call_args_list
        self.assertEqual(calls[0].kwargs["headers"]["Content-Range"], "bytes */8")
        self.assertEqual(calls[1].kwargs["headers"]["Content-Range"], "bytes 0-3/8")
        self.assertEqual(calls[2].kwargs["headers"]["Content-Range"], "bytes 4-7/8")
        self.assertEqual(calls[2].kwargs["data"], b"5678")

    def test_lost_final_response_recovers_existing_id_without_uploading_again(self):
        self.saved_session()
        self.session.put.return_value = response(200, {"id": "videoABC123"})
        self.assertEqual(self.run_upload()["videoId"], "videoABC123")
        self.session.post.assert_not_called()
        self.assertEqual(self.session.put.call_count, 1)
        self.assertEqual(self.session.put.call_args.kwargs["data"], b"")
        self.run_upload()
        self.assertEqual(self.session.put.call_count, 1)

    def test_resume_trusts_server_offset_after_connection_loss(self):
        self.saved_session()
        self.session.put.side_effect = [response(308, headers={"Range": "bytes=0-3"}),
                                        requests.ConnectionError(),
                                        response(200, {"id": "videoABC123"})]
        with patch.object(upload.time, "sleep"):
            self.assertEqual(self.run_upload()["videoId"], "videoABC123")
        self.session.post.assert_not_called()
        self.assertEqual(self.session.put.call_args_list[-1].kwargs["headers"]["Content-Range"], "bytes */8")

    def test_expired_session_requires_review_instead_of_creating_duplicate(self):
        self.saved_session()
        self.session.put.return_value = response(404)
        with self.assertRaises(upload.UploadError):
            self.run_upload()
        self.session.post.assert_not_called()
        self.assertEqual(upload.load_json(self.record)["state"], "REVIEW_REQUIRED")

    def test_different_file_cannot_overwrite_existing_receipt(self):
        self.saved_session()
        self.file.write_bytes(b"abcdefgh")
        with self.assertRaises(upload.UploadError):
            self.run_upload()
        self.session.post.assert_not_called()
        self.session.put.assert_not_called()

    def test_untrusted_session_cannot_receive_credentials_or_video(self):
        state = self.saved_session()
        state["sessionUri"] = "https://googleapis.com.evil.invalid/upload"
        upload.save_json(self.record, state)
        with self.assertRaises(upload.UploadError):
            self.run_upload()
        self.session.put.assert_not_called()

    def test_wrong_channel_stops_before_any_upload(self):
        self.session.get.side_effect = [response(200, {"items": [{"id": "expected"}]}),
                                        response(200, {"items": [{"id": "another"}]})]
        with self.assertRaises(upload.UploadError):
            upload.verify_channel(self.session, "Semillero-Video-Analysis")
        self.session.post.assert_not_called()

    def test_matching_handle_is_compared_by_channel_id(self):
        self.session.get.side_effect = [response(200, {"items": [{"id": "expected"}]}),
                                        response(200, {"items": [{"id": "expected", "snippet": {"title": "Semillero"}}]})]
        self.assertEqual(upload.verify_channel(self.session, "@Semillero-Video-Analysis")["id"], "expected")

    def test_retry_limit_survives_successful_probes_without_progress(self):
        self.saved_session()
        self.session.put.side_effect = [item for _ in range(6) for item in (
            response(308, headers={"Range": "bytes=0-3"}), response(429))]
        with patch.object(upload.time, "sleep"), self.assertRaises(upload.UploadError):
            self.run_upload()
        self.assertEqual(self.session.put.call_count, 12)

    def test_token_files_are_private(self):
        upload.save_json(self.record, {"refresh_token": "fake-test-token"})
        self.assertEqual(stat.S_IMODE(self.record.stat().st_mode), 0o600)

    def test_visibility_mismatch_is_reported_and_keeps_remote_id(self):
        state = self.saved_session()
        state["videoId"] = "videoABC123"
        upload.save_json(self.record, state)
        self.session.get.return_value = response(200, {"items": [{
            "snippet": {"title": "2026-10-21", "channelId": "channel-id"},
            "status": {"privacyStatus": "private", "uploadStatus": "processed"}}]})
        with self.assertRaises(upload.UploadError):
            upload.verify_video(self.session, self.record, 0)
        saved = upload.load_json(self.record)
        self.assertEqual(saved["state"], "METADATA_MISMATCH")
        self.assertEqual(saved["actualPrivacy"], "private")
        self.assertEqual(saved["videoId"], "videoABC123")

    def test_processing_video_is_not_reported_as_available(self):
        state = self.saved_session()
        state["videoId"] = "videoABC123"
        upload.save_json(self.record, state)
        self.session.get.return_value = response(200, {"items": [{
            "snippet": {"title": "2026-10-21", "channelId": "channel-id"},
            "status": {"privacyStatus": "unlisted", "uploadStatus": "uploaded"},
            "processingDetails": {"processingStatus": "processing"}}]})
        self.assertEqual(upload.verify_video(self.session, self.record, 0)["state"], "PROCESSING")


if __name__ == "__main__":
    unittest.main()
