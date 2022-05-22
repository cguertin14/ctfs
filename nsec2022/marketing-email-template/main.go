package main

import (
	"errors"
	"fmt"
	"log"
	"os"
	"path"
	"strings"

	"html/template"
	"io/ioutil"
	"net/http"

	"github.com/gin-gonic/gin"
)

type Update struct {
	Template string `json:"template" binding:"required"`
}

type MyApp struct {
	templateName string
}

func (m *MyApp) ReadFile() ([]byte, error) {
	f, err := os.Open(m.templateName)

	if err != nil {
		files, e := ioutil.ReadDir(path.Dir(m.templateName))

		if e != nil {
			return []byte{}, e
		}

		var filenames []string
		for _, file := range files {
			var filename string = file.Name()
			if file.IsDir() {
				filename += " (directory)"
			}
			filenames = append(filenames, filename)
		}

		return []byte(fmt.Sprint(err) + "\nPossible files:\n" + strings.Join(filenames[:], "\n")), nil
	}

	defer func() {
		if err = f.Close(); err != nil {
			log.Fatal(err)
		}
	}()

	return ioutil.ReadAll(f)
}

func (m *MyApp) SetTemplateName(name string) error {
	if name == "" {
		return errors.New("TemplateName cannot be nil or empty.")
	}

	m.templateName = name
	return nil
}

func main() {
	r := gin.Default()
	r.LoadHTMLGlob("templates/*")
	r.Static("/assets", "./assets")

	r.GET("/", func(c *gin.Context) {
		m := MyApp{"templates/index.html"}
		content, err := m.ReadFile()

		if err != nil {
			log.Fatal(err)
		}

		m.SetTemplateName("templates/preview.html")
		preview, err := m.ReadFile()

		if err != nil {
			log.Fatal(err)
		}

		tmpl, err := template.New("").Parse(string(content))

		if err != nil {
			log.Fatal(err)
		}

		c.Status(http.StatusOK)
		err = tmpl.Execute(c.Writer, gin.H{"title": "Test!", "template": string(preview), "m": &m})

		if err != nil {
			log.Fatal(err)
		}
	})

	r.POST("/update", func(c *gin.Context) {
		var update Update
		var err error

		err = c.BindJSON(&update)

		if err != nil {
			log.Fatal(err)
		}

		f, err := os.OpenFile("templates/preview.html", os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0664)

		if err != nil {
			log.Fatal(err)
		}

		defer f.Close()

		if _, err = f.WriteString(update.Template); err != nil {
			log.Fatal(err)
		}

		c.JSON(200, "ok")
	})

	r.Any("/preview", func(c *gin.Context) {
		m := MyApp{"templates/preview.html"}

		var title string
		if title = c.Query("title"); c.Query("title") == "" {
			title = "Test Email"
		}

		var eventname string
		if eventname = c.Query("eventname"); c.Query("eventname") == "" {
			eventname = "My-Event-Name"
		}

		var firstname string
		if firstname = c.Query("firstname"); c.Query("firstname") == "" {
			firstname = "John"
		}

		var lastname string
		if lastname = c.Query("lastname"); c.Query("lastname") == "" {
			lastname = "Smith"
		}

		var companyname string
		if companyname = c.Query("companyname"); c.Query("companyname") == "" {
			companyname = "Email Templating Inc."
		}

		// Fetch all custom tags
		var num string
		var stop bool = false
		var custom string
		var customs = map[string]string{}
		for i := 1; !stop; i++ {
			num = "custom" + string(i+48)
			custom = c.Query(num)

			if custom == "" {
				stop = true
				break
			}
			customs[num] = custom
		}

		// Create the response map
		var response map[string]interface{} = gin.H{
			"title":       title,
			"eventname":   eventname,
			"firstname":   firstname,
			"lastname":    lastname,
			"companyname": companyname,
			"m":           &m,
		}

		// Merge the custom tags
		for key, value := range customs {
			response[key] = value
		}

		c.HTML(http.StatusOK,
			"preview.html",
			response,
		)

		content, err := m.ReadFile()

		if err != nil {
			log.Fatal(err)
		}

		tmpl, err := template.New("").Parse(string(content))

		if err != nil {
			log.Fatal(err)
		}

		c.Status(http.StatusOK)
		err = tmpl.Execute(c.Writer, response)

		if err != nil {
			log.Fatal(err)
		}
	})

	r.Run(":3000")
}
