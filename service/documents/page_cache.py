import json
import sqlite3


class PageCache:
    def __init__(self, root):
        self.path = root / 'engineered-pages.sqlite3'
        with sqlite3.connect(self.path) as database:
            database.execute('CREATE TABLE IF NOT EXISTS pages(document_id TEXT, page_index INTEGER, version INTEGER, '
                             'payload TEXT NOT NULL, PRIMARY KEY(document_id,page_index,version))')

    def find(self, document_id, page_index, version):
        with sqlite3.connect(self.path) as database:
            row = database.execute('SELECT payload FROM pages WHERE document_id=? AND page_index=? AND version=?',
                                   (document_id, page_index, version)).fetchone()
        return json.loads(row[0]) if row else None

    def save(self, document_id, page_index, version, payload):
        with sqlite3.connect(self.path) as database:
            database.execute('INSERT INTO pages VALUES(?,?,?,?)',
                             (document_id, page_index, version, json.dumps(payload)))
