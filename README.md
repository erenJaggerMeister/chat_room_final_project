# Chat Room Java - Kelompok D Jaringan Komputer
Aplikasi chat berbasis Java Socket dengan antarmuka GUI menggunakan Swing.

# Fitur Utama
1. **Masuk dengan identitas pengguna unik**  
   - Setiap pengguna memasukkan **nama pengguna** saat login.
   - Bisa digunakan di **beberapa komputer berbeda**, asalkan terhubung ke jaringan yang sama.
   - IP server bisa diatur secara manual di form login

2. **Buat dan kelola room**  
   - Pengguna bisa membuat room baru dan menjadi pemiliknya (owner).
   - Pemilik room memiliki hak khusus: menutup room dan mengeluarkan pengguna lain.

3. **Bergabung ke room**  
   - Daftar room akan muncul secara otomatis dan terus diperbarui.
   - Pengguna bisa **klik dua kali** pada nama room untuk bergabung.

4. **Kirim pesan ke seluruh anggota**  
   - Pesan akan diteruskan ke semua anggota yang berada di dalam room yang sama.

5. **Lihat anggota room**  
   - Klik label nama room di pojok atas tampilan chat untuk menampilkan daftar pengguna saat ini.

6. **Tutup room (owner only)**  
   - Klik satu kali room milik sendiri → tombol **"Tutup Room"** akan aktif.
   - Room akan ditutup dan semua anggota dikeluarkan secara otomatis.

7. **Kick user (owner only)**  
   - Pemilik room bisa memilih dan mengeluarkan pengguna tertentu dari room.


# Cara Menjalankan
1. Pastikan semua file `.java` (terutama `ChatServer.java` dan `ChatClientGUI.java`) berada dalam satu folder.
2. Buka command prompt, lalu ketik `java ChatServer.java` untuk menjalankan server.
3. Buka command prompt lainnya, lalu ketik `java ChatClientGUI.java`
4. Setelah aplikasi client terbuka, isi form login dengan data berikut:
    - **Nama**:  
    Nama pengguna yang ingin digunakan (bebas).

    - **Server**:  
    Alamat IP dari server.  
        - Gunakan `127.0.0.1` jika client dan server dijalankan di komputer yang sama.  
        - Gunakan IP LAN server jika dijalankan dari komputer berbeda dalam satu jaringan.

    - **Port**:  
    Gunakan `3355` (port default server).

    Setelah semua terisi, klik tombol **Connect** untuk mulai menggunakan aplikasi.