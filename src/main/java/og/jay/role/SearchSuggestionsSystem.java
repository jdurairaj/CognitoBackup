package com.sai.ds;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class SearchSuggestionsSystem {
    public static void main(String[] args) {
        SearchSuggestionsSystem search=new SearchSuggestionsSystem();
//        System.out.println(search.suggestedProducts(new String[]{"mobile","mouse","moneypot","monitor","mousepad"}, "mouse"));
//        System.out.println(search.suggestedProducts(new String[]{"havana"}, "havana"));
        System.out.println(search.reverse(83457089));
    }

    public int reverse(int x) {
        boolean isNeg=x<0;
        String rev=new StringBuilder(String.valueOf(Math.abs(x))).reverse().toString();
        try{
            return Integer.parseInt(isNeg?"-".concat(rev):rev);
        }catch (Exception ex){
            return 0;
        }
    }

    public List<List<String>> suggestedProducts(String[] products, String searchWord) {
        if(null==products || products.length==0 || null==searchWord || searchWord.isBlank()) return null;
        Trie dict=new Trie();
        Arrays.stream(products).sorted().forEach(w->insert(w, dict));
        List<List<String>>result=new LinkedList<>();
        search(searchWord, dict, result);
        return result;
    }
    public void insert(String word, Trie node){
        if(null==word || word.isBlank()) return;
        for (Character ch:word.toCharArray()){
            int c=ch-'a';
            Trie child= node.child[c];
            if(null==child){
                child=new Trie();
                node.child[c]=child;
            }
            if(child.getSuggestion().size()<3)
                child.getSuggestion().add(word);
            node=child;
        }
    }

    public void search(String word, Trie node, List<List<String>> result){
        if(null==word || word.isBlank() || null==node) return;
        for (Character ch:word.toCharArray()){
            int c=ch-'a';
            Trie child=node.getChild()[c];
            if(null==child) return;
            result.add(child.getSuggestion());
            node=child;
        }
    }



    class Trie{
        Trie[] child=new Trie[26];
        List<String> suggestion=new LinkedList<String>();


        public Trie[] getChild() {
            return child;
        }

        public void setChild(Trie[] child) {
            this.child = child;
        }

        public List<String> getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(List<String> suggestion) {
            this.suggestion = suggestion;
        }
    }
}
