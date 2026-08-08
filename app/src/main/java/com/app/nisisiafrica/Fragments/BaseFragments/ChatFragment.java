package com.app.nisisiafrica.Fragments.BaseFragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.ChatAdapter;
import com.app.nisisiafrica.Adapters.ChatRoomAdapter;
import com.app.nisisiafrica.Adapters.ChatSearchAdapter;
import com.app.nisisiafrica.Adapters.PinnedChatAdapter;
import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.DataBase.AppDatabase;
import com.app.nisisiafrica.Interfaces.NotificationApiService;
import com.app.nisisiafrica.Interfaces.SwipeToReplyCallback;
import com.app.nisisiafrica.ProfileActivity;
import com.app.nisisiafrica.MainActivity;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.Roles;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.ChatRoomViewModel;
import com.app.nisisiafrica.ViewModel.ChatRoomViewModelFactory;
import com.app.nisisiafrica.ViewModel.ChatViewModel;
import com.app.nisisiafrica.data.Model.ChatMessageEntity;
import com.app.nisisiafrica.data.Model.ChatNotificationRequest;
import com.app.nisisiafrica.data.Model.Chatroom;
import com.app.nisisiafrica.data.Model.NotificationResponse;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.Model.MentorItem;
import com.app.nisisiafrica.data.Repository.ChatRepository;
import com.app.nisisiafrica.data.Repository.ChatRoomRepository;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.app.nisisiafrica.data.remote.StorageUploader;
import com.app.nisisiafrica.databinding.FragmentChatBinding;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import kotlin.Unit;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatFragment extends Fragment {
    private ChatViewModel viewModel;
    private ChatAdapter adapter;
    private String chatroomId;
    private ChatRepository chatRepository;

    private FragmentChatBinding binding;
    private ChatRoomViewModel chatRoomViewModel;
    private UserData userData;
    private String joinedAT, role;
    private boolean isChatOpen, isMentor;
    private String selectedOtherUserId;
    private boolean selectedIsMentor = false;
    private OnBackPressedCallback backCallback;
    private ConcatAdapter concatAdapter;
    private ChatSearchAdapter searchAdapter;
    private final java.util.List<Chatroom> loadedRooms = new ArrayList<>();
    private java.util.List<Chatroom> pinnedRooms = new ArrayList<>();
    private ChatRoomAdapter pagedAdapter;
    private String currentChatId;
    private String currentReceiverId;
    private ActivityResultLauncher<PickVisualMediaRequest> imagePicker;
    private ActivityResultLauncher<String> documentPicker;
    private String pendingMediaType = "file";
    private ActionMode messageActionMode;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), uri -> {
                    if (uri != null) sendPickedImage(uri);
                });

        documentPicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) sendPickedMedia(uri, pendingMediaType);
                });

        initConfiguration();
        setupRecyclerView();
        setupGlobalClickListeners();
        setupBackNavigation();
        setupChatGlassChrome();

        binding.btnAttach.setOnClickListener(v -> {
            if (currentChatId == null) {
                Toast.makeText(requireContext(), "Open a chat first", Toast.LENGTH_SHORT).show();
                return;
            }
            showAttachmentChooser();
        });

        // Start-chat is now triggered from MainActivity's contextual bottom-bar FAB.
        binding.fabNewChat.setVisibility(View.GONE);

        // Initialize user metadata and global rooms
        handleUserMetadata();
        chatRoomViewModel.initPinnedChats();


        return binding.getRoot();
    }

    private void initConfiguration() {
        joinedAT = Util.getState(Constants.USER_CREATED_AT, "");
        role = Util.getState(Constants.USER_ROLE, "Mentee");

        ChatRoomRepository repository = new ChatRoomRepository();
        ChatRoomViewModelFactory factory = new ChatRoomViewModelFactory(repository);
        chatRoomViewModel = new ViewModelProvider(this, factory).get(ChatRoomViewModel.class);
    }

    private void setupRecyclerView() {
        // 1. Adapters (Method references for cleaner lambda)
        PinnedChatAdapter pinnedAdapter = new PinnedChatAdapter(chatroom -> {
            openChat(chatroom);
            return Unit.INSTANCE;
        });
        pagedAdapter = new ChatRoomAdapter(chatroom -> {
            openChat(chatroom);
            return Unit.INSTANCE;
        }, chatroom -> {
            confirmDeleteChat(chatroom);
            return Unit.INSTANCE;
        });

        // 2. ConcatAdapter to pin items to top
        concatAdapter = new ConcatAdapter(pinnedAdapter, pagedAdapter);
        binding.rvChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvChats.setAdapter(concatAdapter);

        searchAdapter = new ChatSearchAdapter(this::openChat);

        // 3. Observers
        chatRoomViewModel.getPinnedChatRooms().observe(getViewLifecycleOwner(), rooms -> {
            pinnedAdapter.submitList(rooms);
            pinnedRooms = rooms != null ? rooms : new ArrayList<>();
            rebuildLoadedRooms();
        });

        chatRoomViewModel.getChatRooms().observe(getViewLifecycleOwner(), pagingData -> {
            pagedAdapter.submitData(getViewLifecycleOwner().getLifecycle(), pagingData);
        });

        pagedAdapter.addOnPagesUpdatedListener(() -> {
            rebuildLoadedRooms();
            return Unit.INSTANCE;
        });

        setupChatSearch();
    }

    private void rebuildLoadedRooms() {
        loadedRooms.clear();
        loadedRooms.addAll(pinnedRooms);
        if (pagedAdapter != null) {
            for (Chatroom room : pagedAdapter.snapshot().getItems()) {
                if (room != null) loadedRooms.add(room);
            }
        }
    }

    private void setupChatSearch() {
        binding.etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase(Locale.getDefault());
                if (query.isEmpty()) {
                    binding.rvChats.setAdapter(concatAdapter);
                    return;
                }
                String currentUserId = FirebaseAuth.getInstance().getUid();
                ArrayList<Chatroom> filtered = new ArrayList<>();
                for (Chatroom room : loadedRooms) {
                    String name = room.getOtherUserName(currentUserId);
                    if (name != null && name.toLowerCase(Locale.getDefault()).contains(query)) {
                        filtered.add(room);
                    }
                }
                if (binding.rvChats.getAdapter() != searchAdapter) {
                    binding.rvChats.setAdapter(searchAdapter);
                }
                searchAdapter.submit(filtered);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void openChat(Chatroom chatroom) {
        if (chatroom == null) return;

        String currentUserId = FirebaseAuth.getInstance().getUid();
        String chatId = chatroom.getChatroomId();
        String type = chatroom.getType() != null ? chatroom.getType() : "direct";

        currentChatId = chatId;
        currentReceiverId = chatroom.getOtherUserId(currentUserId);

        showChatDetail();
        binding.etMessage.setText("");
        clearReply();

        updateChatHeader(chatroom, type, currentUserId);

        // Access Control: mentors & admins may post announcements; mentees read only.
        if ("system".equals(type)) {
            boolean canPost = Roles.canCreate(role);
            int vis = canPost ? View.VISIBLE : View.GONE;
            binding.bottomChatBar.setVisibility(vis);
            binding.chatComposerBlur.setVisibility(vis);
        } else {
            binding.bottomChatBar.setVisibility(View.VISIBLE);
            binding.chatComposerBlur.setVisibility(View.VISIBLE);
        }

        startRealtimeMessages(chatId);
        setupSendAction(chatId, chatroom);
        viewModel.markRead(chatId);
    }

    /** Confirms then deletes a chat (skips system/AI/announcement rooms). */
    private void confirmDeleteChat(Chatroom chatroom) {
        if (chatroom == null) return;
        String type = chatroom.getType() != null ? chatroom.getType() : "direct";
        if ("system".equals(type) || "ai".equals(type)) {
            Toast.makeText(requireContext(), "This chat can't be deleted", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentUserId = FirebaseAuth.getInstance().getUid();
        String name = chatroom.getOtherUserName(currentUserId);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete chat")
                .setMessage("Delete your conversation" + (name != null ? " with " + name : "") + "? This can't be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    String chatId = chatroom.getChatroomId();
                    chatRoomViewModel.deleteChatRoom(chatId, ok -> {
                        if (binding == null) return Unit.INSTANCE;
                        Toast.makeText(requireContext(),
                                ok ? "Chat deleted" : "Couldn't delete chat", Toast.LENGTH_SHORT).show();
                        if (ok && chatId.equals(currentChatId)) {
                            showChatList();
                            currentChatId = null;
                        }
                        return Unit.INSTANCE;
                    });
                })
                .show();
    }

    /** Bottom-sheet picker letting a mentor jump into a DM with one of their mentees. */
    public void showNewChatPicker() {
        java.util.List<Chatroom> mentees = new ArrayList<>();
        for (Chatroom r : loadedRooms) {
            String type = r.getType() != null ? r.getType() : "direct";
            if ("direct".equals(type)) mentees.add(r);
        }
        if (mentees.isEmpty()) {
            Toast.makeText(requireContext(),
                    "No mentees yet — they'll appear here once they book you.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        int pad = Math.round(8 * getResources().getDisplayMetrics().density);
        rv.setPadding(0, pad, 0, pad);
        ChatSearchAdapter adapter = new ChatSearchAdapter(room -> {
            dialog.dismiss();
            openChat(room);
        });
        rv.setAdapter(adapter);
        adapter.submit(mentees);
        dialog.setContentView(rv);
        dialog.show();
    }

    /** Lets the user attach a photo, document, or audio file to the open chat. */
    private void showAttachmentChooser() {
        CharSequence[] options = {"Photo", "Document", "Audio"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Send attachment")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            imagePicker.launch(new PickVisualMediaRequest.Builder()
                                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                    .build());
                            break;
                        case 1:
                            pendingMediaType = "file";
                            documentPicker.launch("*/*");
                            break;
                        case 2:
                            pendingMediaType = "audio";
                            documentPicker.launch("audio/*");
                            break;
                    }
                })
                .show();
    }

    private void sendPickedMedia(Uri uri, String type) {
        if (currentChatId == null) return;
        Toast.makeText(requireContext(), "Uploading...", Toast.LENGTH_SHORT).show();
        final String chatId = currentChatId;
        final String receiverId = currentReceiverId;
        final ChatMessageEntity replyTarget = replyingTo;
        clearReply();
        String folder = "audio".equals(type) ? "chat_audio" : "chat_files";
        StorageUploader.upload(uri, folder, (success, url) -> {
            if (binding == null || viewModel == null) return;
            if (success && url != null) {
                viewModel.sendMediaMessage(chatId, url, type, receiverId, replyTarget, ok -> {
                    if (!ok) Toast.makeText(requireContext(), "Not sent", Toast.LENGTH_SHORT).show();
                    return Unit.INSTANCE;
                });
            } else {
                Toast.makeText(requireContext(), "Upload failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendPickedImage(Uri uri) {
        if (currentChatId == null) return;
        Toast.makeText(requireContext(), "Uploading image...", Toast.LENGTH_SHORT).show();
        final String chatId = currentChatId;
        final String receiverId = currentReceiverId;
        final ChatMessageEntity replyTarget = replyingTo;
        clearReply();
        StorageUploader.upload(uri, "chat_images", (success, url) -> {
            if (binding == null || viewModel == null) return;
            if (success && url != null) {
                viewModel.sendImageMessage(chatId, url, receiverId, replyTarget, ok -> {
                    if (!ok) Toast.makeText(requireContext(), "Image not sent", Toast.LENGTH_SHORT).show();
                    return Unit.INSTANCE;
                });
            } else {
                Toast.makeText(requireContext(), "Upload failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateChatHeader(Chatroom chatroom, String type, String currentUserId) {
        binding.btnViewProfile.setVisibility(View.GONE);

        switch (type) {
            case "system":
                binding.tvChatName.setText("Announcements");
                binding.tvChatRole.setText("Official Updates");
                Glide.with(this).load(R.drawable.nelsen_icon).circleCrop().into(binding.tvHeaderAvatar);
                userData = null;
                selectedOtherUserId = null;
                break;
            case "ai":
                binding.tvChatName.setText("Nelsen AI Assistant");
                binding.tvChatRole.setText("Virtual Help");
                //todo update
                Glide.with(this).load(R.drawable.cyborg).circleCrop().into(binding.tvHeaderAvatar);
                userData = null;
                selectedOtherUserId = null;
                break;
            default: // Direct Chat
                binding.btnViewProfile.setVisibility(View.VISIBLE);
                String otherUserId = chatroom.getOtherUserId(currentUserId);
                String otherName = chatroom.getOtherUserName(currentUserId);
                binding.tvChatName.setText(otherName);
                binding.tvChatRole.setText("");
                Glide.with(this).load(R.drawable.ic_person).circleCrop().into(binding.tvHeaderAvatar);

                // Reset immediately so the header never shows the previously opened
                // person while the new one is still loading.
                userData = null;
                selectedOtherUserId = otherUserId;

                if (otherUserId != null) {
                    selectedIsMentor = false;
                    FirebaseRemoteDataSource.INSTANCE.getRemoteUserData(otherUserId, user -> {
                        // Ignore late callbacks from a previously opened chat.
                        if (!otherUserId.equals(selectedOtherUserId)) return Unit.INSTANCE;
                        boolean hasName = user != null
                                && (((user.getFirstName() != null && !user.getFirstName().isEmpty()))
                                || (user.getEmail() != null && !user.getEmail().isEmpty()));
                        if (user != null && hasName) {
                            userData = user;
                            Glide.with(this).load(user.getPhotoUrl()).circleCrop()
                                    .placeholder(R.drawable.ic_person).into(binding.tvHeaderAvatar);
                            binding.tvChatRole.setText(user.getUserRole());
                            selectedIsMentor = "Mentor".equals(user.getUserRole());
                        } else {
                            // The partner is likely a mentor (stored under /mentors, not /users).
                            loadMentorProfileFallback(otherUserId, otherName);
                        }
                        return Unit.INSTANCE;
                    }, e -> {
                        loadMentorProfileFallback(otherUserId, otherName);
                        return Unit.INSTANCE;
                    });

                }
                break;
        }
    }

    /** Fills the header from the /mentors node when the partner has no /users record. */
    private void loadMentorProfileFallback(String mentorId, String fallbackName) {
        FirebaseRemoteDataSource.INSTANCE.getMentorData(mentorId, mentor -> {
            if (binding == null || !mentorId.equals(selectedOtherUserId)) return Unit.INSTANCE;
            if (mentor == null) return Unit.INSTANCE;
            selectedIsMentor = true;
            String name = mentor.getMentorName() != null && !mentor.getMentorName().isEmpty()
                    ? mentor.getMentorName() : fallbackName;
            binding.tvChatName.setText(name != null ? name : "");
            binding.tvChatRole.setText("Mentor");
            Glide.with(this).load(mentor.getMentorImageUrl())
                    .placeholder(R.drawable.ic_person).circleCrop().into(binding.tvHeaderAvatar);
            return Unit.INSTANCE;
        }, e -> Unit.INSTANCE);
    }

    private void startRealtimeMessages(String chatId) {

        if (chatRepository != null) {
            chatRepository.clearSyncListener();
        }

        AppDatabase db = AppDatabase.getInstance(requireContext());
        chatRepository = new ChatRepository(db);
        viewModel = new ViewModelProvider(this, new ChatViewModel.Factory(chatRepository))
                .get(ChatViewModel.class);
        adapter = new ChatAdapter();


        // swipe to reply
        // in startRealtimeMessages, after setting adapter
        binding.rvMessages.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            private float startX, startY;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getX();
                        startY = e.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(e.getX() - startX);
                        float dy = Math.abs(e.getY() - startY);
                        if (dx > dy && dx > 10) {
                            // horizontal swipe — block panels from stealing it
                            rv.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        rv.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {}

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });
        SwipeToReplyCallback swipeCallback = new SwipeToReplyCallback(
                requireContext(),
                position -> {
                    if (position >= 0) {
                        Object item = adapter.getItemAt(position);
                        if (item instanceof ChatMessageEntity) {
                            triggerReply((ChatMessageEntity) item);
                        }
                    }
                    return Unit.INSTANCE;
                }
        );
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvMessages);

        adapter.setOnSelectionChanged(count -> {
            if (count == 0) {
                finishActionMode();
            } else {
                ensureActionMode();
                if (messageActionMode != null) {
                    messageActionMode.setTitle(String.valueOf(count));
                    refreshActionModeMenu();
                }
            }
            return Unit.INSTANCE;
        });
        adapter.setOnQuotedClick(messageId -> {
            scrollToMessage(messageId);
            return Unit.INSTANCE;
        });

         binding.rvMessages.setAdapter(adapter);
         binding.rvMessages.setLayoutManager(new LinearLayoutManager(requireContext()));

        chatRepository.syncMessages(chatId);
        viewModel.loadMessages(chatId);
        viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            adapter.submitList(messages);
            if (!messages.isEmpty() && (messageActionMode == null))
                binding.rvMessages.smoothScrollToPosition(messages.size() - 1);
        });
    }

    private ChatMessageEntity replyingTo = null;

    private void triggerReply(ChatMessageEntity message) {
        if (message.getDeleted()) return;
        replyingTo = message;
        binding.replyPreview.setVisibility(View.VISIBLE);
        binding.tvReplyText.setText(previewOf(message));
        binding.tvReplySender.setText(message.getSenderName());
        binding.btnCancelReply.setOnClickListener(v -> clearReply());
        binding.etMessage.requestFocus();
    }

    private void clearReply() {
        replyingTo = null;
        if (binding != null) binding.replyPreview.setVisibility(View.GONE);
    }

    /** Human-readable stand-in for a message, so attachments never quote a raw URL. */
    private String previewOf(ChatMessageEntity message) {
        String type = message.getType();
        if ("image".equals(type)) return "\uD83D\uDCF7 Photo";
        if ("audio".equals(type)) return "\uD83C\uDFB5 Audio";
        if ("file".equals(type)) return "\uD83D\uDCC4 Document";
        return message.getMessage();
    }

    /**
     * WhatsApp-style ActionMode: reply / forward / delete as top-bar icons
     * while one or more messages are selected.
     */
    private void ensureActionMode() {
        if (messageActionMode != null || !(getActivity() instanceof AppCompatActivity)) return;
        messageActionMode = ((AppCompatActivity) requireActivity())
                .startSupportActionMode(new ActionMode.Callback() {
                    @Override
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        mode.getMenuInflater().inflate(R.menu.chat_message_actions, menu);
                        return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                        refreshActionModeMenu(menu);
                        return true;
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        java.util.List<ChatMessageEntity> selected =
                                new ArrayList<>(adapter.selectedMessages());
                        if (selected.isEmpty()) return false;
                        int id = item.getItemId();
                        if (id == R.id.action_reply) {
                            triggerReply(selected.get(0));
                            mode.finish();
                            return true;
                        }
                        if (id == R.id.action_forward) {
                            // Finish selection UI first, then forward (selected already copied).
                            mode.finish();
                            forwardInApp(selected);
                            return true;
                        }
                        if (id == R.id.action_share) {
                            mode.finish();
                            shareExternally(selected);
                            return true;
                        }
                        if (id == R.id.action_delete) {
                            confirmDeleteSelected(selected);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode) {
                        messageActionMode = null;
                        if (adapter != null) adapter.clearSelection();
                    }
                });
    }

    private void refreshActionModeMenu() {
        if (messageActionMode != null) refreshActionModeMenu(messageActionMode.getMenu());
    }

    private void refreshActionModeMenu(Menu menu) {
        if (menu == null || adapter == null) return;
        java.util.List<ChatMessageEntity> selected = adapter.selectedMessages();
        String uid = FirebaseAuth.getInstance().getUid();
        boolean allMine = !selected.isEmpty();
        for (ChatMessageEntity m : selected) {
            if (uid == null || !uid.equals(m.getSenderId())) {
                allMine = false;
                break;
            }
        }
        MenuItem reply = menu.findItem(R.id.action_reply);
        MenuItem delete = menu.findItem(R.id.action_delete);
        if (reply != null) reply.setVisible(selected.size() == 1);
        if (delete != null) delete.setVisible(allMine);
    }

    private void finishActionMode() {
        if (messageActionMode != null) {
            ActionMode mode = messageActionMode;
            messageActionMode = null;
            mode.finish();
        } else if (adapter != null) {
            adapter.clearSelection();
        }
    }

    /** Forward into another chat (picker). Sends real content/type per message. */
    private void forwardInApp(java.util.List<ChatMessageEntity> selected) {
        if (selected == null || selected.isEmpty() || binding == null) return;
        final java.util.List<ChatMessageEntity> toForward = new ArrayList<>(selected);

        java.util.List<Chatroom> targets = new ArrayList<>();
        String uid = FirebaseAuth.getInstance().getUid();
        for (Chatroom r : loadedRooms) {
            if (r == null || r.getChatroomId() == null) continue;
            if (r.getChatroomId().equals(currentChatId)) continue;
            String type = r.getType() != null ? r.getType() : "direct";
            // Forward into other DMs (not system/AI rooms).
            if ("direct".equals(type)) {
                targets.add(r);
            }
        }
        if (targets.isEmpty()) {
            Toast.makeText(requireContext(),
                    "No other chats to forward to — share instead", Toast.LENGTH_SHORT).show();
            shareExternally(toForward);
            return;
        }

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        RecyclerView rv = new RecyclerView(requireContext());
        int pad = dp(12);
        rv.setPadding(pad, pad, pad, pad);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        ChatSearchAdapter picker = new ChatSearchAdapter(room -> {
            dialog.dismiss();
            if (room == null || room.getChatroomId() == null || viewModel == null) return;
            String me = FirebaseAuth.getInstance().getUid();
            if (me == null) {
                Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show();
                return;
            }
            String receiverId = room.getOtherUserId(me);
            forwardMessagesToRoom(room.getChatroomId(), receiverId, toForward);
        });
        rv.setAdapter(picker);
        picker.submit(targets);
        dialog.setContentView(rv);
        dialog.show();
    }

    /** Sends each selected message into [roomId] preserving type (text/image/file/audio). */
    private void forwardMessagesToRoom(
            String roomId,
            String receiverId,
            java.util.List<ChatMessageEntity> messages
    ) {
        if (messages.isEmpty()) return;
        final int[] remaining = {messages.size()};
        final boolean[] anyFail = {false};
        for (ChatMessageEntity m : messages) {
            if (m == null || m.getDeleted()) {
                if (--remaining[0] == 0) toastForwardResult(!anyFail[0]);
                continue;
            }
            String type = m.getType() != null ? m.getType() : "text";
            kotlin.jvm.functions.Function1<Boolean, Unit> done = ok -> {
                if (!ok) anyFail[0] = true;
                if (--remaining[0] == 0) toastForwardResult(!anyFail[0]);
                return Unit.INSTANCE;
            };
            if ("image".equals(type)) {
                viewModel.sendImageMessage(roomId, m.getMessage(), receiverId, done);
            } else if ("file".equals(type) || "audio".equals(type)) {
                viewModel.sendMediaMessage(roomId, m.getMessage(), type, receiverId, done);
            } else {
                viewModel.sendMessage(roomId, m.getMessage(), receiverId, done);
            }
        }
    }

    private void toastForwardResult(boolean ok) {
        if (getContext() == null) return;
        Toast.makeText(requireContext(),
                ok ? "Forwarded" : "Couldn't forward some messages", Toast.LENGTH_SHORT).show();
    }

    private void shareExternally(java.util.List<ChatMessageEntity> selected) {
        StringBuilder body = new StringBuilder();
        for (ChatMessageEntity m : selected) {
            if (body.length() > 0) body.append("\n\n");
            body.append(previewOf(m));
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, body.toString());
        startActivity(Intent.createChooser(share, "Share"));
    }

    private void confirmDeleteSelected(java.util.List<ChatMessageEntity> selected) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(selected.size() == 1 ? "Delete message?" : "Delete " + selected.size() + " messages?")
                .setMessage("They will be replaced with \"This message was deleted\" for everyone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    if (currentChatId == null) return;
                    for (ChatMessageEntity message : selected) {
                        if (replyingTo != null
                                && replyingTo.getMessageId().equals(message.getMessageId())) {
                            clearReply();
                        }
                        viewModel.deleteMessage(currentChatId, message, ok -> {
                            if (!ok && binding != null) {
                                Toast.makeText(requireContext(),
                                        "Couldn't delete — check Firestore rules allow message updates",
                                        Toast.LENGTH_LONG).show();
                            }
                            return Unit.INSTANCE;
                        });
                    }
                    finishActionMode();
                })
                .show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("message", text));
        Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show();
    }

    /** Jumps to the quoted original and flashes it; no-ops when it isn't loaded. */
    private void scrollToMessage(String messageId) {
        if (binding == null || adapter == null) return;
        int position = adapter.positionOf(messageId);
        if (position < 0) {
            Toast.makeText(requireContext(), "Original message not available", Toast.LENGTH_SHORT).show();
            return;
        }
        binding.rvMessages.smoothScrollToPosition(position);
        adapter.flashMessage(messageId);
    }

    private void setupSendAction(String chatId, Chatroom chatroom) {
        binding.btnSend.setOnClickListener(v -> {
            String msg = binding.etMessage.getText().toString().trim();
            if (!msg.isEmpty()) {
                binding.etMessage.setText("");
                ChatMessageEntity replyTarget = replyingTo;
                clearReply();
                String currentUserId = FirebaseAuth.getInstance().getUid();
                String receiverId = chatroom.getOtherUserId(currentUserId);
                viewModel.sendMessage(chatId, msg, receiverId, replyTarget, success -> {
                    if (!success) {
                        Toast.makeText(requireContext(), "Message not sent", Toast.LENGTH_SHORT).show();
                    }
                    if (receiverId != null) {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        user.getIdToken(false).addOnSuccessListener(result -> {
                            String token = "Bearer " + result.getToken();
                            ChatNotificationRequest request = new ChatNotificationRequest(
                                    receiverId,
                                    msg,
                                    chatId
                            );
                            NotificationApiService apiService = ApiClient.getClient().create(NotificationApiService.class);
                            apiService.sendChatNotification(token, request)
                                    .enqueue(new Callback<NotificationResponse>() {
                                        @Override
                                        public void onResponse(Call<NotificationResponse> call, Response<NotificationResponse> response) {}
                                        @Override
                                        public void onFailure(Call<NotificationResponse> call, Throwable t) {
                                            Log.e("ChatNotif", "Failed", t);
                                        }
                                    });
                        });
                    }
                    return Unit.INSTANCE;
                });
            }
        });
    }

//    private void setupSendAction(String chatId) {
//        binding.btnSend.setOnClickListener(v -> {
//            String msg = binding.etMessage.getText().toString().trim();
//            if (!msg.isEmpty()) {
//                binding.etMessage.setText("");
//                viewModel.sendMessage(chatId, msg, success -> {
//                    if (!success) {
//                        Toast.makeText(requireContext(), "Message not sent", Toast.LENGTH_SHORT).show();
//                    }
//                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
//                    user.getIdToken(false).addOnSuccessListener(result -> {
//                        String token = "Bearer " + result.getToken();
//                        Chatroom chatroom = new Chatroom();
//                        ChatNotificationRequest request = new ChatNotificationRequest(
//                                chatroom.getOtherUserId(chatId),
//                                msg,      // the message content
//                                chatId    // your conversation/chat room ID
//                        );
//
//                        NotificationApiService apiService = ApiClient.getClient().create(NotificationApiService.class);
//
//                        apiService.sendChatNotification(token, request)
//                                .enqueue(new Callback<>() {
//                                    @Override
//                                    public void onResponse(Call<NotificationResponse> call,
//                                                           Response<NotificationResponse> response) {
//                                        // silent, no need to handle
//                                    }
//
//                                    @Override
//                                    public void onFailure(Call<NotificationResponse> call, Throwable t) {
//                                        Log.e("ChatNotif", "Failed to send notification", t);
//                                    }
//                                });
//                    });
//                    return Unit.INSTANCE;
//                });
//
//            }
//        });
//    }

    private void setupGlobalClickListeners() {
        binding.allChatInfo.setOnClickListener(v -> openPartnerProfile());
        binding.icBack.setOnClickListener(v -> showChatList());
    }

    /** Opens the conversation partner's full profile. No-op for system/AI rooms. */
    private void openPartnerProfile() {
        if (selectedOtherUserId == null) return;
        Intent intent = new Intent(getActivity(), ProfileActivity.class);
        intent.putExtra(selectedIsMentor ? Constants.MENTOR_ID : Constants.USER_ID, selectedOtherUserId);
        startActivity(intent);
    }

    /** Translucent header + composer (wash only — BlurView cannot nest inside BlurTarget). */
    private void setupChatGlassChrome() {
        if (binding == null || getActivity() == null) return;
        if (binding.chatHeaderBlur == null || binding.chatComposerBlur == null) return;
        int overlay = ContextCompat.getColor(requireContext(), R.color.blur_overlay);
        binding.chatHeaderBlur.setBackgroundColor(overlay);
        binding.chatComposerBlur.setBackgroundColor(overlay);

        if (binding.llHeader != null) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.llHeader, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                v.setPadding(v.getPaddingLeft(), bars.top + dp(10), v.getPaddingRight(), dp(12));
                return insets;
            });
        }
        if (binding.bottomChatBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.bottomChatBar, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                v.setPadding(v.getPaddingLeft(), dp(8), v.getPaddingRight(), dp(8) + Math.max(0, bars.bottom / 4));
                return insets;
            });
        }
        if (binding.chatDetailContainer != null) {
            ViewCompat.requestApplyInsets(binding.chatDetailContainer);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Shows the open conversation and hides the bottom nav so the input has room. */
    private void showChatDetail() {
        if (binding == null) return;
        isChatOpen = true;
        binding.chatListContainer.setVisibility(View.GONE);
        binding.chatDetailContainer.setVisibility(View.VISIBLE);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setChatConversationOpen(true);
        }
        ViewCompat.requestApplyInsets(binding.chatDetailContainer);
    }

    /** Returns to the conversation list. */
    private void showChatList() {
        if (binding == null) return;
        isChatOpen = false;
        binding.chatDetailContainer.setVisibility(View.GONE);
        binding.chatListContainer.setVisibility(View.VISIBLE);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setChatConversationOpen(false);
        }
        Util.saveState(Constants.IS_MENTOR, false);
        userData = null;
        selectedOtherUserId = null;
        clearReply();
        hideKeyboard();
        finishActionMode();
    }

    private void handleUserMetadata() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && joinedAT.isEmpty()) {
            Date createdAtDate = new Date(user.getMetadata().getCreationTimestamp());
            Util.saveState(Constants.USER_CREATED_AT, createdAtDate.toString());
        }
    }

    private void setupBackNavigation() {
        // Disabled by default; only intercepts back while the chat tab is the
        // visible fragment. Otherwise Home's back press would never exit the app.
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (messageActionMode != null) {
                    finishActionMode();
                } else if (isChatOpen) {
                    showChatList();
                } else if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateToHomeTab();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (backCallback != null) backCallback.setEnabled(!hidden);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Sync back-handling to actual visibility once the preload show/hide settles.
        view.post(() -> {
            if (backCallback != null) backCallback.setEnabled(!isHidden());
        });
    }

    private void hideKeyboard() {
        View view = getActivity() != null ? getActivity().getCurrentFocus() : null;
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroyView() {
        if (chatRepository != null) {
            chatRepository.clearSyncListener();
        }
        super.onDestroyView();
    }
}
